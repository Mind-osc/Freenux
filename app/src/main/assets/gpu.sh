#!/bin/bash
# Freenux GPU (πειραματικό): Turnip (Vulkan για Adreno) + Zink (OpenGL πάνω σε Vulkan).
# Γίνεται source από το desktop-x11.sh. Αν δεν δουλέψει, μένουμε σε software χωρίς να κολλήσει τίποτα.
[ -f /root/.freenux/gpu.on ] || return 0 2>/dev/null || exit 0

echo ">>> GPU: έλεγχος κάρτας γραφικών…"
if ! command -v vulkaninfo >/dev/null; then
  echo ">>> GPU: λείπουν πακέτα – πάτα ① για ενημέρωση"
  return 0 2>/dev/null || exit 0
fi

VKOUT=$(TU_DEBUG=noconform MESA_VK_WSI_DEBUG=sw timeout 40 vulkaninfo --summary 2>&1); VKRC=$?
DEV=$(echo "$VKOUT" | grep -i "deviceName" | grep -vi "llvmpipe" | head -1 | sed 's/.*= *//')
echo ">>> GPU Vulkan: ${DEV:-δεν βρέθηκε}"
if [ -z "$DEV" ]; then
  echo ">>> [GPU διάγνωση] vulkaninfo κωδικός $VKRC (124 = timeout)"
  echo ">>> [GPU διάγνωση] συσκευή κάρτας:"; ls -l /dev/kgsl* 2>&1 | sed 's/^/   /'
  echo ">>> [GPU διάγνωση] drivers Vulkan:"; ls /usr/share/vulkan/icd.d/ 2>&1 | sed 's/^/   /'
  echo ">>> [GPU διάγνωση] έξοδος vulkaninfo:"; echo "$VKOUT" | grep -v "^$" | tail -12 | sed 's/^/   /'
fi
case "$DEV" in
  *Adreno*|*Turnip*|*turnip*) ;;
  *)
    echo ">>> GPU: δεν βρέθηκε driver για Adreno (Turnip/KGSL) – πάτα ① για ενημέρωση· μέχρι τότε μένει ο επεξεργαστής"
    return 0 2>/dev/null || exit 0 ;;
esac

# 1) Patched Mesa: freedreno πάνω σε KGSL, εικόνα κατευθείαν στο Termux:X11 (DRI3)
if [ -f /root/.freenux/mesa-patched ] && [ ! -f /root/.freenux/gpu-direct-failed ]; then
  GLK=$(env -u LIBGL_DRI3_DISABLE -u LIBGL_ALWAYS_SOFTWARE MESA_LOADER_DRIVER_OVERRIDE=kgsl TU_DEBUG=noconform \
        timeout 15 glxinfo -B 2>/dev/null | grep -m1 "OpenGL renderer" | sed 's/.*: *//')
  if [ -n "$GLK" ] && ! echo "$GLK" | grep -qi "llvmpipe\|softpipe"; then
    unset LIBGL_DRI3_DISABLE LIBGL_ALWAYS_SOFTWARE MESA_VK_WSI_DEBUG GALLIUM_DRIVER
    export MESA_LOADER_DRIVER_OVERRIDE=kgsl TU_DEBUG=noconform
    echo ">>> GPU ✓ ενεργή (απευθείας, patched $(cat /root/.freenux/mesa-patched)): $GLK"
    return 0 2>/dev/null || exit 0
  fi
  echo ">>> GPU: ο patched driver δεν απάντησε (${GLK:-timeout}) – δοκιμή Zink"
fi

[ -f /root/.freenux/gpu-direct-failed ] && echo ">>> GPU: ο απευθείας driver είχε κολλήσει το desktop – χρησιμοποιώ Zink (σβήσε /root/.freenux/gpu-direct-failed για νέα δοκιμή)"

# 2) Zink πάνω σε Turnip, με αντιγραφή εικόνας από τον επεξεργαστή (δουλεύει πάντα)
GLR=$(MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink TU_DEBUG=noconform MESA_VK_WSI_DEBUG=sw LIBGL_KOPPER_DRI2=1 \
      LIBGL_ALWAYS_SOFTWARE= timeout 15 glxinfo -B 2>/dev/null | grep -m1 "OpenGL renderer" | sed 's/.*: *//')
if [ -n "$GLR" ] && ! echo "$GLR" | grep -qi "llvmpipe\|softpipe"; then
  unset LIBGL_ALWAYS_SOFTWARE
  export MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink TU_DEBUG=noconform MESA_VK_WSI_DEBUG=sw LIBGL_KOPPER_DRI2=1
  echo ">>> GPU ✓ ενεργή: $GLR"
else
  echo ">>> GPU: το OpenGL δεν δούλεψε (${GLR:-timeout}) – μένει ο επεξεργαστής"
fi
