# Base Droid v2 - AOSP Build Integration Directory

Welcome to the **Base Droid v2** developer integration environment. This directory serves as the binding mount point to merge standard Android Open Source Project (AOSP) source trees with Base Droid's premium features, hyper-visual UI layers, and secure kernel modifications.

## Structure of this Folder

1. `build_aosp.sh`: Automatically initializes standard AOSP workspaces, links Base Droid overlays, runs compilation targets under `lunch` and compiles flashable partitions.
2. `manifest.xml`: Repo Manifest configuration to bind custom applications such as Launcher, Settings, Store, and kernel-hardening branches directly during sync phase.

## Quick Integration Workflow

1. Place your native AOSP codebase on your development server (Ubuntu 22.04 LTS / 24.04 LTS recommended).
2. Copy the contents of this folder into your AOSP workspace root.
3. Source and verify with:
   ```bash
   chmod +x build_aosp.sh
   ./build_aosp.sh --target aosp_arm64-userdebug
   ```

Refer to the main `README.md` at the project root for complete physical hardware flashing details, system specifications, partition configurations, and developer utilities.
