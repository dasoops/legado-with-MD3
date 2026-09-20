#!/usr/bin/env bash

set -euo pipefail

channel="${1:-}"
version="${2:-}"

if [[ "$channel" != "official" && "$channel" != "beta" ]]; then
  echo "Unsupported update channel: $channel" >&2
  exit 1
fi
if [[ -z "$version" ]]; then
  echo "Release version is required" >&2
  exit 1
fi
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"

manifest_branch="update-manifests"
manifest_dir="$RUNNER_TEMP/$manifest_branch"
release_tmp="$manifest_dir/release.tmp"
release_list_tmp="$manifest_dir/releases.tmp"

# 带重试的 gh api: release 刚创建时按 tag 查询偶发 404, 重试可规避时序问题
gh_api() {
  local path="$1"
  local attempt
  for attempt in 1 2 3 4 5; do
    if gh api \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2026-03-10" \
      "$path"; then
      return 0
    fi
    echo "gh api $path failed (attempt $attempt/5), retrying..." >&2
    sleep $((attempt * 3))
  done
  return 1
}

# 先按 tag 精确查询, 失败则回退到 releases 列表按 tag_name 过滤
fetch_release_by_tag() {
  local tag="$1"
  local out="$2"
  if gh_api "repos/$GITHUB_REPOSITORY/releases/tags/$tag" > "$out"; then
    return 0
  fi
  echo "releases/tags/$tag unavailable, falling back to releases list" >&2
  gh_api "repos/$GITHUB_REPOSITORY/releases?per_page=100" > "$release_list_tmp"
  jq -e --arg tag "$tag" '[.[] | select(.tag_name == $tag)] | .[0]' "$release_list_tmp" > "$out"
}

write_manifest() {
  local source_file="$1"
  local target_channel="$2"
  local expected_version="$3"
  local target_file="$manifest_dir/$target_channel.json"
  local target_tmp="$target_file.tmp"
  local expected_prerelease="false"
  if [[ "$target_channel" == "beta" ]]; then
    expected_prerelease="true"
  fi

  jq -e \
    --arg version "$expected_version" \
    --argjson prerelease "$expected_prerelease" \
    'if .tag_name == $version and .prerelease == $prerelease and (.assets | type == "array") then
      {
        tag_name,
        name,
        body: (.body // ""),
        prerelease,
        created_at,
        assets: [.assets[] | {
          browser_download_url,
          content_type,
          created_at,
          download_count: 0,
          id,
          name,
          state,
          url
        }]
      }
    else
      error("Release response does not match the requested channel and version")
    end' \
    "$source_file" > "$target_tmp"
  mv "$target_tmp" "$target_file"
}

rm -rf "$manifest_dir"
if git fetch --no-tags origin "$manifest_branch"; then
  git worktree add --detach "$manifest_dir" FETCH_HEAD
else
  git worktree add --detach "$manifest_dir" HEAD
  git -C "$manifest_dir" checkout --orphan "$manifest_branch"
  git -C "$manifest_dir" rm -q -rf --ignore-unmatch .
fi
trap 'git worktree remove --force "$manifest_dir" >/dev/null 2>&1 || true' EXIT

fetch_release_by_tag "$version" "$release_tmp"
write_manifest "$release_tmp" "$channel" "$version"

other_channel="official"
if [[ "$channel" == "official" ]]; then
  other_channel="beta"
fi

if [[ ! -f "$manifest_dir/$other_channel.json" ]]; then
  if [[ "$other_channel" == "official" ]]; then
    # 仓库可能尚无正式版 release, 此时 releases/latest 返回 404, 属于正常情况
    if gh api \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2026-03-10" \
      "repos/$GITHUB_REPOSITORY/releases/latest" > "$release_tmp" 2>/dev/null; then
      other_version="$(jq -r '.tag_name' "$release_tmp")"
      write_manifest "$release_tmp" "$other_channel" "$other_version"
    else
      echo "no official release yet; skipping $other_channel manifest" >&2
    fi
  else
    gh_api "repos/$GITHUB_REPOSITORY/releases?per_page=100" > "$release_list_tmp"
    if jq -e \
      '[.[] | select(.draft == false and .prerelease == true)] | max_by(.created_at) | select(. != null)' \
      "$release_list_tmp" > "$release_tmp"; then
      other_version="$(jq -r '.tag_name' "$release_tmp")"
      write_manifest "$release_tmp" "$other_channel" "$other_version"
    fi
  fi
fi
rm -f "$release_tmp" "$release_list_tmp"

git -C "$manifest_dir" config user.name "github-actions[bot]"
git -C "$manifest_dir" config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git -C "$manifest_dir" add --all

if git -C "$manifest_dir" diff --cached --quiet; then
  echo "$channel update manifest is already current"
  exit 0
fi

git -C "$manifest_dir" commit -m "Update release manifests for $version"
git -C "$manifest_dir" push origin "HEAD:refs/heads/$manifest_branch"
