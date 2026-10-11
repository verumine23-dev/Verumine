#!/usr/bin/env bash
# =============================================================================
#  Test du VRAI calcul de hash Verus (pas seulement des fonctions isolées).
#
#  On compile le code de hash de ccminer pour ARM 64 bits et ARM 32 bits avec les
#  mêmes options que l'APK, on calcule 4000 hashes sur les mêmes données, et on compare.
#  Le 64 bits sert de référence (c'est le code officiel pour ARM).
#
#  Deux versions du code 32 bits sont testées :
#    - « d'origine »  : le code tel quel
#    - « corrigé »    : avec la correction appliquée par build-android.sh
#                       (0x10000000L << rounds  ->  (uint64_t)0x10000000 << rounds)
#
#  Utilisation (GitHub Actions) : ANDROID_NDK_HOME=... bash miner/difftest/verus_hash_test.sh
#  Compilateurs remplaçables par CC64 / CXX64 / CC32 / CXX32 (test sur PC Linux).
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
W="${WORK_DIR:-$ROOT/hashtest-work}"
CCMINER_REPO="${CCMINER_REPO:-https://github.com/Oink70/CCminer-ARM-optimized.git}"
CCMINER_REF="${CCMINER_REF:-main}"
rm -rf "$W" && mkdir -p "$W"

if [ -n "${ANDROID_NDK_HOME:-}" ]; then
  TC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
  CC64="${CC64:-$TC/aarch64-linux-android26-clang}"
  CXX64="${CXX64:-$TC/aarch64-linux-android26-clang++}"
  CC32="${CC32:-$TC/armv7a-linux-androideabi26-clang}"
  CXX32="${CXX32:-$TC/armv7a-linux-androideabi26-clang++}"
fi
OPT64="${OPT64:--march=armv8-a+crypto -mtune=cortex-a53}"
OPT32="${OPT32:--march=armv8-a+crypto -mfpu=crypto-neon-fp-armv8 -mfloat-abi=softfp}"
COMMON="-O3 -w -static -flax-vector-conversions -fno-strict-aliasing -fomit-frame-pointer -fno-stack-protector"

# --- En-tête de compatibilité : exactement celui de l'APK ---
ABI=armeabi-v7a ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-/inexistant}" bash "$ROOT/miner/build-android.sh" header >/dev/null 2>&1 || true
COMPAT="$ROOT/miner/work/armeabi-v7a/android-compat.h"
if [ ! -s "$COMPAT" ]; then
  echo "En-tête de compatibilité introuvable ($COMPAT)"; exit 1
fi

# --- Code de ccminer ---
git clone -q --depth 1 --branch "$CCMINER_REF" "$CCMINER_REPO" "$W/src" 2>/dev/null \
  || git clone -q --depth 1 "$CCMINER_REPO" "$W/src"
echo "Code ccminer : $(git -C "$W/src" rev-parse HEAD)"

for v in orig fixed; do
  mkdir -p "$W/$v"
  cp "$W"/src/verus/*.c "$W"/src/verus/*.cpp "$W"/src/verus/*.h "$W/$v/"
  cp "$W/src/sse2neon.h" "$W/$v/"
  sed -i 's/__ARM_ARCH == 7/__ARM_ARCH >= 7/g' "$W/$v/sse2neon.h"
done
sed -i 's/0x10000000L << rounds/(uint64_t)0x10000000 << rounds/g' "$W/fixed/verus_clhash_portable.cpp"
echo "Lignes corrigées dans la version « corrigé » : $(grep -c '(uint64_t)0x10000000 << rounds' "$W/fixed/verus_clhash_portable.cpp")"

# --- Programme de test : reprend les fonctions de hash de verusscan.cpp ---
python3 - "$W/src/verus/verusscan.cpp" "$W/harness.cpp" <<'PY'
import sys, re
src = open(sys.argv[1], errors='ignore').read()
a = src.index('void GenNewCLKey')
b = src.index('#ifdef _WIN32', src.index('extern "C" void Verus2hash'))
body = src[a:b]
head = r'''
#include <algorithm>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdint.h>
#define VERUS_KEY_SIZE 8832
#define VERUS_KEY_SIZE128 552
#include "sse2neon.h"
extern "C" {
#include "haraka_portable.h"
#include "haraka.h"
}
uint64_t verusclhash_port2_2(void * random, const unsigned char buf[64], uint64_t keyMask, uint16_t * __restrict fixrand,
                             uint16_t * __restrict fixrandex, __m128i *g_prand, __m128i *g_prandex);
#if defined(__ANDROID__)
/* Android exige un segment de données par thread bien aligné */
__attribute__((used)) static __thread char tls_pad[1] __attribute__((aligned(64))) = {1};
#endif
'''
tail = r'''
static uint64_t rs = 0x9E3779B97F4A7C15ULL;
static uint64_t rnd(void) { rs ^= rs << 13; rs ^= rs >> 7; rs ^= rs << 17; return rs; }
static __m128i data_key[VERUS_KEY_SIZE] __attribute__((aligned(64)));
static __m128i pr[32 * 16] __attribute__((aligned(64))), prx[32 * 16] __attribute__((aligned(64)));
int main() {
#if defined(__ANDROID__)
  if (tls_pad[0] == 2) return 3;
#endif
  uint8_t full[1487];
  alignas(16) uint8_t half[64];
  uint16_t fr[32] __attribute__((aligned(64))), frx[32] __attribute__((aligned(64)));
  uint8_t gi = 0;
  for (int h = 0; h < 20; h++) {
    for (int i = 0; i < 1487; i++) full[i] = (uint8_t)rnd();
    VerusHashHalf(half, full, 1487);
    GenNewCLKey(half, data_key);
    uint8_t nonce[15];
    for (int i = 0; i < 15; i++) nonce[i] = (uint8_t)rnd();
    for (uint32_t n = 0; n < 200; n++) {
      memcpy(nonce + 11, &n, 4);
      alignas(16) uint8_t curbuf[64];
      memcpy(curbuf, half, 64);
      uint32_t vhash[8] = {0};
      Verus2hash((unsigned char *)vhash, curbuf, nonce, data_key, &gi, fr, frx, pr, prx, 0);
      printf("%08x\n", vhash[7]);
    }
  }
  return 0;
}
'''
open(sys.argv[2], 'w').write(head + body + tail)
PY

build() {  # build <nom> <dossier> <cc> <cxx> <options>
  local name="$1" dir="$2" cc="$3" cxx="$4" opt="$5" b="$W/b_$1" f
  mkdir -p "$b"
  f="$COMMON $opt -I$W/$dir -include $COMPAT"
  # shellcheck disable=SC2086
  { $cc $f -c "$W/$dir/haraka.c" -o "$b/haraka.o" &&
    $cc $f -c "$W/$dir/haraka_portable.c" -o "$b/hp.o" &&
    $cxx $f -std=gnu++11 -c "$W/$dir/verus_clhash_portable.cpp" -o "$b/vc.o" &&
    $cxx $f -std=gnu++11 -c "$W/harness.cpp" -o "$b/h.o" &&
    $cxx $f "$b/h.o" "$b/vc.o" "$b/hp.o" "$b/haraka.o" -o "$b/run"; } > "$b/build.log" 2>&1
}

run() {  # run <nom> <qemu>
  "$2" -cpu max "$W/b_$1/run" > "$W/out_$1.txt" 2> "$W/b_$1/run.log"
}

same() { paste -d' ' "$W/out_$1.txt" "$W/out_$2.txt" | awk '$1==$2' | wc -l; }

ok=1
for spec in "a64|orig|$CC64|$CXX64|$OPT64|qemu-aarch64-static" \
            "a32|orig|$CC32|$CXX32|$OPT32|qemu-arm-static" \
            "a32fix|fixed|$CC32|$CXX32|$OPT32|qemu-arm-static"; do
  IFS='|' read -r n d cc cxx opt q <<< "$spec"
  if ! build "$n" "$d" "$cc" "$cxx" "$opt"; then
    echo "Compilation impossible ($n) :"; echo '```'; grep -E 'error' "$W/b_$n/build.log" | head -15; echo '```'; ok=0; continue
  fi
  if ! run "$n" "$q" || [ "$(wc -l < "$W/out_$n.txt")" -ne 4000 ]; then
    echo "Exécution impossible ($n) :"; echo '```'; head -c 1200 "$W/b_$n/run.log"; echo '```'; ok=0
  fi
done
[ "$ok" = 1 ] || exit 1

s_orig="$(same a64 a32)"
s_fix="$(same a64 a32fix)"
echo
echo "| Programme 32 bits | Hashes identiques au 64 bits |"
echo "|---|---|"
echo "| code d'origine (celui des APK précédents) | **$s_orig / 4000** |"
echo "| code corrigé (nouveau build-android.sh) | **$s_fix / 4000** |"
echo
if [ "$s_fix" = 4000 ]; then
  echo "Résultat : le 32 bits corrigé calcule exactement comme le 64 bits."
else
  echo "Résultat : il reste une différence dans le 32 bits corrigé."
fi
