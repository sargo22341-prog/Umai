#!/bin/sh
set -eu

for attempt in 1 2 3; do
    if timeout --kill-after=30 600 bazel --batch --output_user_root=/tmp/bazel-cache build \
        --config=android_arm64 --jobs=4 --repo_env=CC=/usr/bin/gcc --repo_env=CXX=/usr/bin/g++ \
        @litert//litert/vendors/google_tensor/dispatch:dispatch_api_so > /tmp/dispatch.log 2>&1; then
        cat /tmp/dispatch.log
        mkdir -p /artifact
        cp bazel-bin/external/litert/litert/vendors/google_tensor/dispatch/libLiteRtDispatch_GoogleTensor.so /artifact/
        exit 0
    fi
    cat /tmp/dispatch.log >&2
    # Docker Desktop DNS can fail transiently. Retry only that transport error;
    # compiler, ABI, checksum and timeout failures always stop the build.
    if ! grep -Eq 'Unknown host:|UnknownHostException|Temporary failure in name resolution' /tmp/dispatch.log; then
        exit 1
    fi
    if [ "$attempt" -eq 3 ]; then
        exit 1
    fi
    echo "Retrying transient DNS failure (attempt $attempt of 3)" >&2
done
exit 1
