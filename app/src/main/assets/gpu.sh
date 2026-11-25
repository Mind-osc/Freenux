#!/bin/bash
# Freenux GPU (πειραματικό): Turnip (Vulkan για Adreno) + Zink (OpenGL πάνω σε Vulkan).
# Γίνεται source από το desktop-x11.sh. Αν δεν δουλέψει, μένουμε σε software χωρίς να κολλήσει τίποτα.
[ -f /root/.freenux/gpu.on ] || return 0 2>/dev/null || exit 0

echo ">>> GPU: έλεγχος κάρτας γραφικών…"
if ! command -v vulkaninfo >/dev/null; then
  echo ">>> GPU: λείπουν πακέτα – πάτα ① για ενημέρωση"
  return 0 2>/dev/null || exit 0
fi

DEV=$(TU_DEBUG=noconform MESA_VK_WSI_DEBUG=sw timeout 15 vulkaninfo --summary 2>/dev/null | grep -i -m1 "deviceName" | sed 's/.*= *//')
echo ">>> GPU Vulkan: ${DEV:-δεν βρέθηκε}"
case "$DEV" in
  *Adreno*|*Turnip*|*turnip*) ;;
  *)
    echo ">>> GPU: δεν βρέθηκε driver για Adreno (Turnip/KGSL) – μένει ο επεξεργαστής"
    return 0 2>/dev/null || exit 0 ;;
esac

# Δοκιμή OpenGL μέσω Zink με timeout· αν κολλήσει, δεν το ενεργοποιούμε
GLR=$(MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink TU_DEBUG=noconform MESA_VK_WSI_DEBUG=sw \
      LIBGL_ALWAYS_SOFTWARE= timeout 15 glxinfo -B 2>/dev/null | grep -m1 "OpenGL renderer" | sed 's/.*: *//')
if [ -n "$GLR" ] && ! echo "$GLR" | grep -qi "llvmpipe\|softpipe"; then
  unset LIBGL_ALWAYS_SOFTWARE
  export MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink TU_DEBUG=noconform MESA_VK_WSI_DEBUG=sw
  echo ">>> GPU ✓ ενεργή: $GLR"
else
  echo ">>> GPU: το OpenGL δεν δούλεψε (${GLR:-timeout}) – μένει ο επεξεργαστής"
fi
