#!/usr/bin/env python3
"""Fetch official Android build tools into .tooling; preserve inherited proxy/CA settings."""
import concurrent.futures
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.parse
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tooling'


def fetch(name, url):
    target = TOOLS / name
    if target.exists():
        return target
    partial = target.with_suffix(target.suffix + '.partial')
    print('Downloading ' + name, flush=True)
    with urllib.request.urlopen(url, timeout=30) as response, partial.open('wb') as dest:
        shutil.copyfileobj(response, dest)
    partial.replace(target)
    return target


def extract_zip(archive, target):
    with zipfile.ZipFile(archive) as source:
        source.extractall(target)
        for item in source.infolist():
            permissions = item.external_attr >> 16
            if permissions:
                (target / item.filename).chmod(permissions)


def main():
    TOOLS.mkdir(exist_ok=True)
    files = [
        ('android-tools.zip', 'https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip'),
        ('gradle.zip', 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip'),
        ('jdk.tar.gz', 'https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse'),
    ]
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        list(pool.map(lambda entry: fetch(*entry), files))
    sdk = TOOLS / 'android-sdk'
    sdk.mkdir(exist_ok=True)
    commandline = sdk / 'cmdline-tools' / 'latest'
    if not commandline.exists():
        unpack = TOOLS / 'unpack-android'
        unpack.mkdir(exist_ok=True)
        extract_zip(TOOLS / 'android-tools.zip', unpack)
        commandline.parent.mkdir(exist_ok=True)
        shutil.move(str(unpack / 'cmdline-tools'), commandline)
    if not (TOOLS / 'gradle-8.11.1').exists():
        extract_zip(TOOLS / 'gradle.zip', TOOLS)
    jdkdir = TOOLS / 'jdk'
    if not jdkdir.exists():
        jdkdir.mkdir()
        with tarfile.open(TOOLS / 'jdk.tar.gz') as source:
            source.extractall(jdkdir, filter='data')
    jdk = next(jdkdir.glob('*/bin/java')).parent.parent
    env = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk), JAVA_HOME=str(jdk))
    env['PATH'] = str(jdk / 'bin') + os.pathsep + env.get('PATH', '')
    # Java tools do not automatically honor HTTPS_PROXY. Retain the session proxy.
    proxy = urllib.parse.urlparse(env.get('HTTPS_PROXY', env.get('https_proxy', '')))
    java_opts = env.get('JAVA_TOOL_OPTIONS', '')
    if proxy.hostname:
        java_opts += f' -Dhttps.proxyHost={proxy.hostname} -Dhttps.proxyPort={proxy.port or 80}'
        java_opts += f' -Dhttp.proxyHost={proxy.hostname} -Dhttp.proxyPort={proxy.port or 80}'
    if Path('/etc/ssl/certs/java/cacerts').exists():
        java_opts += ' -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
    env['JAVA_TOOL_OPTIONS'] = java_opts.strip()
    manager = str(commandline / 'bin' / 'sdkmanager')
    subprocess.run([manager, '--licenses'], input='y\n' * 120, text=True, env=env, check=True)
    subprocess.run([manager, 'platforms;android-35', 'build-tools;35.0.0', 'platform-tools'], env=env, check=True)
    subprocess.run([str(TOOLS / 'gradle-8.11.1/bin/gradle'), '--no-daemon', ':app:assembleDebug'], cwd=ROOT, env=env, check=True)
    print('APK: ' + str(ROOT / 'app/build/outputs/apk/debug/app-debug.apk'))


if __name__ == '__main__':
    main()
