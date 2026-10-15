#!/usr/bin/env python3
"""
Directly pushes a built Android APK to a GitHub Release via the GitHub REST API.
Zero external dependencies required (uses standard library urllib.request).

Usage:
    export GITHUB_TOKEN="ghp_xxx"
    export GITHUB_REPOSITORY="owner/repo"
    python3 scripts/push_to_github_release.py \
        --apk app/build/outputs/apk/debug/app-debug.apk \
        --tag v1.0.1 \
        --title "Release v1.0.1" \
        --body "Automated build release"
"""

import argparse
import datetime
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request


def make_request(url, method="GET", data=None, headers=None):
    if headers is None:
        headers = {}
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            content = resp.read()
            return resp.status, content
    except urllib.error.HTTPError as e:
        content = e.read()
        return e.code, content


def get_or_create_release(api_base, token, tag, title, body, prerelease=False, draft=False):
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "VocalExtractor-Release-Tool",
        "X-GitHub-Api-Version": "2022-11-28",
    }

    # 1. Check if release already exists for this tag
    get_url = f"{api_base}/releases/tags/{tag}"
    status, resp = make_request(get_url, method="GET", headers=headers)
    if status == 200:
        rel = json.loads(resp.decode("utf-8"))
        print(f"[*] Found existing release for tag '{tag}' (ID: {rel.get('id')})")
        return rel

    # 2. Create new release
    create_url = f"{api_base}/releases"
    payload = {
        "tag_name": tag,
        "name": title or f"Release {tag}",
        "body": body or f"Release created on {datetime.datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')}",
        "draft": draft,
        "prerelease": prerelease,
    }
    data = json.dumps(payload).encode("utf-8")
    headers["Content-Type"] = "application/json"
    status, resp = make_request(create_url, method="POST", data=data, headers=headers)
    if status in (200, 201):
        rel = json.loads(resp.decode("utf-8"))
        print(f"[+] Successfully created release '{rel.get('name')}' (ID: {rel.get('id')})")
        return rel
    else:
        print(f"[!] Error creating release (HTTP {status}): {resp.decode('utf-8', errors='replace')}", file=sys.stderr)
        sys.exit(1)


def upload_asset(upload_url_template, token, apk_path, asset_name, existing_assets):
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "VocalExtractor-Release-Tool",
        "X-GitHub-Api-Version": "2022-11-28",
    }

    # If an asset with this name already exists, delete it first to avoid HTTP 422
    for asset in existing_assets:
        if asset.get("name") == asset_name:
            asset_id = asset.get("id")
            delete_url = f"https://api.github.com/repos/{asset['url'].split('/repos/')[1].split('/releases/')[0]}/releases/assets/{asset_id}"
            print(f"[*] Removing existing asset '{asset_name}' (ID: {asset_id})...")
            status, _ = make_request(delete_url, method="DELETE", headers=headers)
            if status in (204, 200):
                print(f"[+] Removed old asset {asset_id}")
            else:
                print(f"[!] Warning: Failed to delete old asset {asset_id}")

    # Prepare upload URL
    clean_upload_url = upload_url_template.split("{")[0]
    upload_url = f"{clean_upload_url}?name={urllib.parse.quote(asset_name)}"

    file_size = os.path.getsize(apk_path)
    print(f"[*] Uploading '{asset_name}' ({file_size / (1024*1024):.2f} MB) to {upload_url}...")

    headers["Content-Type"] = "application/vnd.android.package-archive"
    headers["Content-Length"] = str(file_size)

    with open(apk_path, "rb") as f:
        apk_data = f.read()

    status, resp = make_request(upload_url, method="POST", data=apk_data, headers=headers)
    if status in (200, 201):
        asset_info = json.loads(resp.decode("utf-8"))
        print(f"[+] Successfully uploaded asset '{asset_name}'!")
        print(f"    Download URL: {asset_info.get('browser_download_url')}")
        return asset_info
    else:
        print(f"[!] Upload failed (HTTP {status}): {resp.decode('utf-8', errors='replace')}", file=sys.stderr)
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description="Push built APK directly to GitHub Release")
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY"),
                        help="GitHub repository in owner/repo format (or GITHUB_REPOSITORY env)")
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN"),
                        help="GitHub Personal Access Token (or GITHUB_TOKEN / GH_TOKEN env)")
    parser.add_argument("--apk", default="app/build/outputs/apk/debug/app-debug.apk",
                        help="Path to the built APK file")
    parser.add_argument("--tag", default=None,
                        help="Release tag name (e.g., v1.0.0). Defaults to v1.0.<timestamp>")
    parser.add_argument("--title", default=None, help="Release title")
    parser.add_argument("--body", default=None, help="Release description/notes")
    parser.add_argument("--asset-name", default=None, help="Target filename in release (default: app-debug.apk)")
    parser.add_argument("--prerelease", action="store_true", help="Mark as pre-release")
    parser.add_argument("--draft", action="store_true", help="Create as draft release")

    args = parser.parse_args()

    if not args.apk or not os.path.isfile(args.apk):
        print(f"[!] Error: APK file not found at '{args.apk}'", file=sys.stderr)
        print("    Build the APK first via: gradle assembleDebug", file=sys.stderr)
        sys.exit(1)

    if not args.token:
        print("[!] Error: GitHub Token is required via --token or GITHUB_TOKEN environment variable.", file=sys.stderr)
        print("    Example: export GITHUB_TOKEN='ghp_your_token_here'", file=sys.stderr)
        sys.exit(1)

    if not args.repo or "/" not in args.repo:
        print("[!] Error: GitHub repository must be in 'owner/repo' format via --repo or GITHUB_REPOSITORY.", file=sys.stderr)
        print("    Example: --repo myuser/VocalExtractor", file=sys.stderr)
        sys.exit(1)

    tag = args.tag
    if not tag:
        tag = f"v1.0.{datetime.datetime.utcnow().strftime('%Y%m%d%H%M')}"

    api_base = f"https://api.github.com/repos/{args.repo}"
    print(f"[*] Target GitHub Repository: {args.repo}")
    print(f"[*] Release Tag: {tag}")
    print(f"[*] Source APK: {args.apk}")

    release = get_or_create_release(
        api_base=api_base,
        token=args.token,
        tag=tag,
        title=args.title or f"Release {tag}",
        body=args.body or f"Automated release with built Android APK ({os.path.basename(args.apk)}).",
        prerelease=args.prerelease,
        draft=args.draft
    )

    upload_url_template = release.get("upload_url", "")
    existing_assets = release.get("assets", [])

    asset_name = args.asset_name or os.path.basename(args.apk)
    upload_asset(upload_url_template, args.token, args.apk, asset_name, existing_assets)

    print("\n[SUCCESS] APK successfully pushed to GitHub Release!")
    print(f"Release URL: {release.get('html_url')}")


if __name__ == "__main__":
    main()
