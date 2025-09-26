# Nyrod

### **Legal Disclaimer**

This project is intended for educational and research purposes only. The information and code provided are for learning about software security, reverse engineering, and cryptographic principles.

**No Warranty:** This software is provided "as is," without warranty of any kind, express or implied. The authors and contributors disclaim all warranties, including but not limited to the warranties of merchantability, fitness for a particular purpose, and non-infringement.

**Limitation of Liability:** In no event shall the authors or copyright holders be liable for any claim, damages, or other liability, whether in an action of contract, tort, or otherwise, arising from, out of, or in connection with the software or the use or other dealings in the software.

**Responsible Use:** You are responsible for your own actions. The authors and contributors of this project will not be held responsible for any misuse of the information or code provided herein. This project is not intended for and should not be used for any illegal activities or to circumvent the copyright of any software.

## About

This is a research project demonstrating Java Agent instrumentation, bytecode manipulation using ASM, RSA cryptography, and JavaFX UI development. The project showcases educational concepts in reverse engineering and software security analysis.

## Build Instructions

This project uses [Gradle](https://gradle.org/).
To build and run the application, use the *Gradle* tool window by clicking the Gradle icon in the right-hand toolbar,
or run it directly from the terminal:

* Run `./gradlew run` to build and run the application.
* Run `./gradlew build` to only build the application.
* Run `./gradlew check` to run all checks, including tests.
* Run `./gradlew clean` to clean all build outputs.

Note the usage of the Gradle Wrapper (`./gradlew`).
This is the suggested way to use Gradle in production projects.

[Learn more about the Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

[Learn more about Gradle tasks](https://docs.gradle.org/current/userguide/command_line_interface.html#common_tasks).

This project uses a version catalog (see `gradle/libs.versions.toml`) to declare and version dependencies
and both a build cache and a configuration cache (see `gradle.properties`).

## VirusTotal & Hybrid Analysis
https://www.virustotal.com/gui/file/2acfab7963aa82a0cbcf811b6e0990fce3ab446e5f977f4253b73ba791e96be1?nocache=1

http://hybrid-analysis.com/sample/2acfab7963aa82a0cbcf811b6e0990fce3ab446e5f977f4253b73ba791e96be1
