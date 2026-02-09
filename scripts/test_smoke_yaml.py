#!/usr/bin/env python3
"""Validate the smoke-test.yml GitHub Actions workflow configuration.

Checks:
  - YAML syntax is valid
  - Required top-level keys are present (name, on, jobs)
  - Workflow trigger is workflow_dispatch with expected inputs
  - Build job: correct runner, JDK setup, Gradle build, artifact upload
  - Smoke job: depends on build, has emulator wait + install/launch steps
  - Repository references point to the correct repo (maceip/MediaMaid)
  - Package name matches the application ID (ai.musicconverter)
"""

import sys
import os
import yaml

WORKFLOW_PATH = os.path.join(
    os.path.dirname(__file__), "..", ".github", "workflows", "smoke-test.yml"
)

EXPECTED_REPO = "maceip/MediaMaid"
EXPECTED_PACKAGE = "ai.musicconverter"

errors = []
warnings = []


def error(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def check_yaml_syntax(path):
    """Parse the YAML file and return the parsed document.

    Note: PyYAML parses the YAML key ``on`` as boolean True.
    We normalize it back to the string ``"on"`` so later checks work.
    """
    try:
        with open(path) as f:
            doc = yaml.safe_load(f)
        if doc is None:
            error("YAML file is empty")
            return None
        # PyYAML interprets bare `on:` as boolean True — normalize it.
        if isinstance(doc, dict) and True in doc:
            doc["on"] = doc.pop(True)
        return doc
    except yaml.YAMLError as e:
        error(f"YAML syntax error: {e}")
        return None
    except FileNotFoundError:
        error(f"File not found: {path}")
        return None


def check_top_level_keys(doc):
    """Verify required top-level keys exist."""
    for key in ("name", "on", "jobs"):
        if key not in doc:
            error(f"Missing required top-level key: '{key}'")


def check_trigger(doc):
    """Verify the workflow uses workflow_dispatch with 'ref' input."""
    trigger = doc.get("on", {})
    if "workflow_dispatch" not in trigger:
        error("Missing 'workflow_dispatch' trigger")
        return
    wd = trigger["workflow_dispatch"]
    if wd is None:
        warn("workflow_dispatch has no inputs defined")
        return
    inputs = wd.get("inputs", {})
    if "ref" not in inputs:
        warn("workflow_dispatch missing 'ref' input")
    else:
        ref_input = inputs["ref"]
        if ref_input.get("type") != "string":
            warn("'ref' input should be of type 'string'")


def check_permissions(doc):
    """Verify permissions are set."""
    perms = doc.get("permissions")
    if perms is None:
        warn("No permissions block defined (defaults to broad permissions)")


def check_build_job(jobs):
    """Validate the build job configuration."""
    if "build" not in jobs:
        error("Missing 'build' job")
        return

    build = jobs["build"]
    steps = build.get("steps", [])

    # Check runner
    runs_on = build.get("runs-on", "")
    if "ubuntu" not in str(runs_on):
        warn(f"Build job runs-on '{runs_on}' — expected ubuntu-based runner")

    # Must have checkout step
    step_uses = [s.get("uses", "") for s in steps]
    if not any("actions/checkout" in u for u in step_uses):
        error("Build job missing actions/checkout step")

    # Must have JDK setup
    if not any("actions/setup-java" in u for u in step_uses):
        error("Build job missing actions/setup-java step")

    # Check JDK version consistency
    for step in steps:
        if "actions/setup-java" in step.get("uses", ""):
            jdk_ver = step.get("with", {}).get("java-version", "")
            if jdk_ver and jdk_ver not in ("17", "21"):
                warn(f"Unusual JDK version: {jdk_ver}")

    # Must have Gradle setup or direct gradle invocation
    has_gradle = any("gradle" in u.lower() for u in step_uses)
    has_gradlew = any("gradlew" in str(s.get("run", "")) for s in steps)
    if not has_gradle and not has_gradlew:
        error("Build job missing Gradle setup or gradlew invocation")

    # Must build an APK
    if not any("assembleDebug" in str(s.get("run", "")) for s in steps):
        error("Build job doesn't run assembleDebug")

    # Must upload artifact
    if not any("actions/upload-artifact" in u for u in step_uses):
        error("Build job missing artifact upload step")


def check_smoke_job(jobs):
    """Validate the smoke test job configuration."""
    if "smoke" not in jobs:
        error("Missing 'smoke' job")
        return

    smoke = jobs["smoke"]

    # Must depend on build
    needs = smoke.get("needs", [])
    if isinstance(needs, str):
        needs = [needs]
    if "build" not in needs:
        error("Smoke job must depend on 'build' job (needs: build)")

    steps = smoke.get("steps", [])
    step_uses = [s.get("uses", "") for s in steps]
    step_runs = [str(s.get("run", "")) for s in steps]
    all_run_text = " ".join(step_runs)

    # Must download artifact
    if not any("actions/download-artifact" in u for u in step_uses):
        error("Smoke job missing artifact download step")

    # Must wait for emulator
    if not any("adb wait-for-device" in r for r in step_runs):
        warn("Smoke job should wait for emulator (adb wait-for-device)")

    # Must install the APK
    if not any("adb install" in r for r in step_runs):
        error("Smoke job missing APK install step (adb install)")

    # Must launch the app
    if not any("adb shell am start" in r for r in step_runs):
        error("Smoke job missing app launch step (adb shell am start)")

    # Must check for crashes
    if not any("FATAL EXCEPTION" in r for r in step_runs):
        warn("Smoke job should check for FATAL EXCEPTION in logcat")

    # Check package name
    if EXPECTED_PACKAGE not in all_run_text:
        error(
            f"Smoke job doesn't reference expected package '{EXPECTED_PACKAGE}'"
        )


def check_repo_references(path):
    """Ensure all repo references point to the correct repository."""
    with open(path) as f:
        content = f.read()

    # Check for any repo references that don't match expected
    import re

    repo_refs = re.findall(r"maceip/[\w-]+", content)
    for ref in repo_refs:
        if ref != EXPECTED_REPO:
            error(
                f"Incorrect repo reference '{ref}' — expected '{EXPECTED_REPO}'"
            )


def main():
    path = os.path.abspath(WORKFLOW_PATH)
    print(f"Validating: {path}")
    print("=" * 60)

    # 1. YAML syntax
    doc = check_yaml_syntax(path)
    if doc is None:
        print_results()
        return 1

    # 2. Top-level structure
    check_top_level_keys(doc)

    # 3. Trigger configuration
    check_trigger(doc)

    # 4. Permissions
    check_permissions(doc)

    # 5. Jobs
    jobs = doc.get("jobs", {})
    if not jobs:
        error("No jobs defined")
    else:
        check_build_job(jobs)
        check_smoke_job(jobs)

    # 6. Repo references
    check_repo_references(path)

    return print_results()


def print_results():
    if warnings:
        print(f"\nWarnings ({len(warnings)}):")
        for w in warnings:
            print(f"  ⚠  {w}")

    if errors:
        print(f"\nErrors ({len(errors)}):")
        for e in errors:
            print(f"  ✗  {e}")
        print(f"\nRESULT: FAIL ({len(errors)} error(s), {len(warnings)} warning(s))")
        return 1
    else:
        print(f"\nRESULT: PASS (0 errors, {len(warnings)} warning(s))")
        return 0


if __name__ == "__main__":
    sys.exit(main())
