#!/usr/bin/env bash

set -euo pipefail

WARMUP=
if [ "${AIR_STARTUP_MODE:-}" = warmup ]; then
	WARMUP=1
fi

log() {
	printf '[startup] %s\n' "$*"
}

run_in_dev_shell() {
	if command -v nix >/dev/null 2>&1; then
		nix develop --command bash -c "$*"
		return
	fi

	if [ -n "${HTTPS_PROXY:-}" ]; then
		proxy_host=${HTTPS_PROXY#*://}
		proxy_host=${proxy_host%%:*}
		proxy_port=${HTTPS_PROXY##*:}
		export GRADLE_OPTS="${GRADLE_OPTS:-} -Dhttp.proxyHost=${proxy_host} -Dhttp.proxyPort=${proxy_port} -Dhttps.proxyHost=${proxy_host} -Dhttps.proxyPort=${proxy_port} -Dhttp.nonProxyHosts=localhost\|127.*"
	fi
	export JAVA_HOME=${JAVA_HOME:-$HOME/.cache/walkthrough-plugin/jdk-21}
	export PATH="$JAVA_HOME/bin:$PATH"
	bash -c "$*"
}

prepare_jdk() {
	local jdk_root="$HOME/.cache/walkthrough-plugin/jdk-21"
	local archive="$HOME/.cache/walkthrough-plugin/jdk-21.tar.gz"
	if [ -x "$jdk_root/bin/java" ]; then
		export JAVA_HOME="$jdk_root"
		export PATH="$JAVA_HOME/bin:$PATH"
		return
	fi

	mkdir -p "$(dirname "$jdk_root")"
	log 'Downloading the Java 21 toolchain'
	curl -fsSL -x "${HTTPS_PROXY:-}" \
		'https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse' \
		-o "$archive"
	rm -rf "$jdk_root"
	mkdir -p "$jdk_root"
	tar -xzf "$archive" --strip-components=1 -C "$jdk_root"
	export JAVA_HOME="$jdk_root"
	export PATH="$JAVA_HOME/bin:$PATH"

	local profile="$HOME/.profile"
	for candidate in "$HOME/.bash_profile" "$HOME/.bash_login" "$HOME/.profile"; do
		if [ -f "$candidate" ]; then
			profile="$candidate"
			break
		fi
	done
	if ! grep -Fq '# walkthrough-plugin Java 21' "$profile" 2>/dev/null; then
		printf "\n# walkthrough-plugin Java 21\nexport JAVA_HOME=\"\$HOME/.cache/walkthrough-plugin/jdk-21\"\nexport PATH=\"\$JAVA_HOME/bin:\$PATH\"\n" >>"$profile"
	fi
	if ! grep -Fq '# walkthrough-plugin Java 21' "$HOME/.bashrc" 2>/dev/null; then
		printf "\n# walkthrough-plugin Java 21\nexport JAVA_HOME=\"\$HOME/.cache/walkthrough-plugin/jdk-21\"\nexport PATH=\"\$JAVA_HOME/bin:\$PATH\"\n" >>"$HOME/.bashrc"
	fi
}

healthcheck() {
	log 'Running the Gradle test suite as the readiness check'
	run_in_dev_shell './gradlew test --no-daemon'
	log 'Healthcheck passed: the Gradle test suite completed'
}

if command -v nix >/dev/null 2>&1; then
	log 'Priming Nix development environment'
	nix develop --command true
else
	log 'Nix is unavailable; using the repository Gradle wrapper and installed JDK'
fi

prepare_jdk

if [ -n "${WARMUP:-}" ]; then
	healthcheck
fi

log 'Startup complete'
