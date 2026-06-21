# Base Droid v2 - Custom Android OS Integration & Control Center

Welcome to the **Base Droid v2** developer and build integration workspace. This project contains both the **Base Droid Interactive Dashboard & Control Simulator** (Android Application) and the system directories to integrate, patch, and build a full Custom Android OS based on the **Android Open Source Project (AOSP)**.

Base Droid v2 is engineered as a secure, performance-packed, developer-focused custom Android ROM which delivers a rich cybernetic aesthetic, advanced kernel protections, real-time background optimization, and built-in developer tools.

---

## 🚀 Complete AOSP Setup & Compilation Guide

Follow these steps to synchronize, customize, and compile the **Base Droid v2** Operating System on a workstation or compilation server.

### 1. Build Machine Prerequisites
Compiling AOSP requires a powerful Linux workstation.
- **Operating System**: Ubuntu 22.04 LTS or Ubuntu 24.04 LTS (64-bit).
- **CPU**: Intel Xeon / AMD Threadripper or Ryzen (Minimum 16 cores, 32+ recommended).
- **RAM**: Minimum 32 GB RAM (64 GB or more highly recommended to prevent Out-Of-Memory errors during link-time optimization).
- **Storage**: Minimum 400 GB of high-speed SSD space (NVMe recommended).

### 2. Prepare the Host Environment
Execute the following commands to install required libraries, compiler toolchains, and build tools on your Ubuntu host:

```bash
sudo apt-get update
sudo apt-get install -y git-core gnupg flex bison build-essential zip curl zlib1g-dev \
  gcc-multilib g++-multilib libc6-dev-i386 libncurses5 lib32ncurses5-dev x11proto-core-dev \
  libx11-dev lib32z1-dev libgl1-mesa-dev libxml2-utils xsltproc unzip fontconfig \
  make rsync python3 python3-pip libssl-dev bc
```

Set up git-repo (the system tool used to manage AOSP branch structures):
```bash
mkdir -p ~/bin
PATH=~/bin:$PATH
curl https://storage.googleapis.com/git-repo-downloads/repo > ~/bin/repo
chmod a+x ~/bin/repo
```

### 3. Initialize and Sync AOSP Repository
Create a working directory for your compilation and initialize the standard Android 14 / Android 15 branch:

```bash
mkdir -p ~/basedroid_workspace
cd ~/basedroid_workspace

# Initialize the desired Android OS baseline branch (e.g., Android 14 QPR3)
repo init -u https://android.googlesource.com/platform/manifest -b android-14.0.0_r50
```

### 4. Link Base Droid v2 Customizations
Inject the custom Base Droid v2 overlays and prebuilts. Copy the configurations located in this workspace's `/aosp` directory into your AOSP root:

```bash
# Clone or copy Base Droid manifest extensions
cp -r /path/to/this/project/aosp/* ~/basedroid_workspace/
```

Synchronize all repositories (this will download AOSP repositories as well as Base Droid's custom Launcher, Settings, App Store, and security-hardened branches in parallel):

```bash
repo sync -c -j$(nproc) --force-sync --no-clone-bundle
```

### 5. Apply Framework Overlays & Boot Animation
Base Droid v2 features a signature cybernetic user interface and custom-designed branding assets. Run the integration patches and merge resources:

```bash
# Ensure execution permissions on the build bootstrapper
chmod +x build_aosp.sh

# Run the build bootstrapper to bind elements and set active overlays
./build_aosp.sh
```

### 6. Compilation Executables
Select your target device and build flavor using the `lunch` environment. For example, generic ARM64 emulation or Snapdragon evaluation target:

```bash
source build/envsetup.sh

# For physical 64-bit devices or generic targets:
lunch aosp_arm64-userdebug

# Start the full compilation using all available hardware cores
make -j$(nproc)
```

### 7. Flashing the Compiled Firmware
Once compilation finishes successfully, files are populated in `out/target/product/generic_arm64/`. Flash your target hardware using standard ADB and Fastboot utilities:

```bash
# Reboot the device into Fastboot bootloader mode
adb reboot bootloader

# Unlock critical bootloader partitions (if not previously unlocked)
fastboot flashing unlock

# Flash the compiled partitions automatically (system, boot, vendor, etc.)
fastboot flashall -w
```

---

## 🎨 Base Droid v2 Application Simulator

Since physical device ROM development requires dedicated host servers, we have also developed a fully-interactive **Base Droid v2 Custom OS Dashboard** (Jetpack Compose Android App) located in `/app/`.

This application serves as an advanced demo environment where developers and users can experience, configure, and inspect all features of Base Droid OS directly inside an Android Emulator or physical device:

### Key Interactive Features of the Simulator:
1. **Interactive Cyber Boot Animation**: Plays a realistic scrolling Linux kernel boot up console sequence displaying system parameters and launching the Base Droid glowing logo before entering the OS.
2. **Launchable AOSP System Apps**:
   - **Base Droid Settings**: Tweak theme animations, toggle Developer Mode, and configure secure VPN nodes.
   - **High-Tech Gemini AI Assistant**: Runs a direct REST connection to search Google Gemini AI, offering device diagnostics, translations, and smart system suggestions.
   - **AOSP Build Console Grid**: Run interactive virtual compilation builds (`make -j8 systemimage`) and witness real-time scrolling build output logs.
   - **Customization Engine**: Tweak colors and dynamic wallpaper schemes (Neon Grid, Slate, Solar), changing the global dashboard appearance in real-time.
   - **Terminal Emulator**: Execute shell commands like `neofetch`, `secure-scan`, `aosp-stats`, and `help` inside a monospace cyber terminal container.
   - **Security Shield Dashboard**: Execute real-time mock malware scanning, monitor active apps memory footprint, and inspect system permissions safely.
   - **AOSP Code Editor**: Edit custom system scripts (HTML, JS, Python) with code execution logic!

---

## ⚒️ Developer Settings and Troubleshooting
If compilation times exceed expectations on your local setup, ensure you optimize:
- **Compiler Cache**: Enable `ccache` to speed up subsequent rebuilds:
  `export USE_CCACHE=1 && prebuilts/misc/linux-x86/ccache/ccache -M 50G`
- **Memory Allocation**: To prevent memory spikes, ensure you set Java Heap bounds:
  `export _JAVA_OPTIONS="-Xmx16g"`
