#!/usr/bin/env python3
"""
Ensures app/google-services.json exists and contains a valid client configuration
matching the project's applicationId (from app/build.gradle.kts).
"""

import json
import os
import re
import sys

def main():
    # 1. Detect applicationId from app/build.gradle.kts
    app_id = "com.aistudio.uvrvocalplayer.xrqkm"
    gradle_file = "app/build.gradle.kts"
    if os.path.exists(gradle_file):
        try:
            with open(gradle_file, "r", encoding="utf-8") as f:
                match = re.search(r'applicationId\s*=\s*["\']([^"\']+)["\']', f.read())
                if match:
                    app_id = match.group(1).strip()
        except Exception as e:
            print(f"[*] Note reading applicationId from {gradle_file}: {e}")

    print(f"[*] Target Application ID: {app_id}")

    gs_path = "app/google-services.json"

    # 2. Check if GOOGLE_SERVICES_SECRET environment variable was provided
    secret_val = os.environ.get("GOOGLE_SERVICES_SECRET", "").strip()
    if secret_val:
        try:
            # Validate it is valid JSON before writing
            json.loads(secret_val)
            print(f"[*] Using GOOGLE_SERVICES_SECRET from environment.")
            with open(gs_path, "w", encoding="utf-8") as f:
                f.write(secret_val)
        except Exception as e:
            print(f"[!] Warning: GOOGLE_SERVICES_SECRET is not valid JSON: {e}")

    default_config = {
        "project_info": {
            "project_number": "000000000000",
            "project_id": "placeholder-project",
            "storage_bucket": "placeholder.appspot.com"
        },
        "client": [
            {
                "client_info": {
                    "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
                    "android_client_info": {
                        "package_name": app_id
                    }
                },
                "oauth_client": [],
                "api_key": [{"current_key": "AIzaSyPlaceholderKeyForBuild00000000"}],
                "services": {}
            }
        ],
        "configuration_version": "1"
    }

    # 3. If file exists, verify or update the client entry
    if os.path.exists(gs_path):
        try:
            with open(gs_path, "r", encoding="utf-8") as f:
                data = json.load(f)

            clients = data.setdefault("client", [])
            has_matching = False
            for c in clients:
                pkg = c.get("client_info", {}).get("android_client_info", {}).get("package_name")
                if pkg == app_id:
                    has_matching = True
                    break

            if not has_matching:
                print(f"[*] No matching client for {app_id} found in existing {gs_path}. Adding client...")
                clients.append({
                    "client_info": {
                        "mobilesdk_app_id": f"1:000000000000:android:000000000000000000000{len(clients)}",
                        "android_client_info": {
                            "package_name": app_id
                        }
                    },
                    "oauth_client": [],
                    "api_key": [{"current_key": "AIzaSyPlaceholderKeyForBuild00000000"}],
                    "services": {}
                })
                with open(gs_path, "w", encoding="utf-8") as f:
                    json.dump(data, f, indent=2)
                print(f"[✓] Successfully added client for {app_id} to {gs_path}")
            else:
                print(f"[✓] Matching client for {app_id} verified in {gs_path}")
        except Exception as e:
            print(f"[!] Error parsing existing {gs_path}: {e}. Replacing with valid default config...")
            with open(gs_path, "w", encoding="utf-8") as f:
                json.dump(default_config, f, indent=2)
    else:
        print(f"[*] Creating new {gs_path} with package {app_id}...")
        os.makedirs(os.path.dirname(gs_path), exist_ok=True)
        with open(gs_path, "w", encoding="utf-8") as f:
            json.dump(default_config, f, indent=2)
        print(f"[✓] Created {gs_path}")

if __name__ == "__main__":
    main()
