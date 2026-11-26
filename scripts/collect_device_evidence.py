#!/usr/bin/env python3
"""Read-only ADB preflight. Does not install, clear app data, run inference or certify a device."""
import argparse
import datetime
import json
import re
import subprocess
from pathlib import Path

PACKAGE="io.github.tomorrowisfire.aiinpocket"

def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=15, check=True).stdout.strip()

def read(serial, *args):
    try:
        return adb("-s",serial,"shell",*args)
    except (OSError, subprocess.SubprocessError):
        return None

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--serial",help="ADB selector; not written to report")
    parser.add_argument("--out",type=Path,required=True)
    args=parser.parse_args()
    try:
        rows=adb("devices").splitlines()[1:]
        devices=[r.split()[0] for r in rows if len(r.split())>=2 and r.split()[1]=="device"]
        serial=args.serial or (devices[0] if len(devices)==1 else None)
        if not serial or serial not in devices:
            raise ValueError("Connect and authorize exactly one device, or specify --serial")
        props={}
        for key in ("ro.product.model","ro.build.version.sdk","ro.build.version.release","ro.product.cpu.abi"):
            value=read(serial,"getprop",key)
            props[key]=(value or "")[:160] or None
        page=read(serial,"getconf","PAGE_SIZE") or read(serial,"getconf","PAGESIZE")
        page=int(page) if page and page.isdigit() else None
        dump=read(serial,"dumpsys","package",PACKAGE) or ""
        installed={}
        for field, pattern in {"versionCode":r"\bversionCode=(\d+)","versionName":r"\bversionName=([^\s]+)","targetSdk":r"\btargetSdk=(\d+)"}.items():
            match=re.search(pattern,dump)
            installed[field]=match.group(1)[:80] if match else None
        report={"status":"PREFLIGHT_ONLY","timestamp_utc":datetime.datetime.now(datetime.timezone.utc).isoformat(),
                "device":props,"page_size_bytes":page,"display_size":(read(serial,"wm","size") or "")[:200],
                "display_density":(read(serial,"wm","density") or "")[:200],"package":PACKAGE,
                "installed_metadata":installed,
                "scope":"read-only metadata; no screenshots, logs, serial, personal chats, keys, installs or functional tests"}
    except (ValueError,OSError,subprocess.SubprocessError) as exc:
        report={"status":"BLOCKED","reason":type(exc).__name__,"scope":"ADB preflight unavailable"}
    args.out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(report["status"],args.out)
    return int(report["status"]=="BLOCKED")

if __name__=="__main__":
    raise SystemExit(main())
