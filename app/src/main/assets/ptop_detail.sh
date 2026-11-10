#!/system/bin/sh
# Per-process detail page. Usage: ptop_detail.sh PID
export LC_ALL=C
P=/proc/$1
if [ ! -d "$P" ] || [ ! -r "$P/stat" ]; then
  echo "@@GONE"
  exit 1
fi

echo "@@CMD"
tr '\0' ' ' < "$P/cmdline" 2>/dev/null
echo
echo "@@EXE"
readlink "$P/exe" 2>/dev/null
echo "@@CWD"
readlink "$P/cwd" 2>/dev/null
echo "@@FDS"
ls "$P/fd" 2>/dev/null | wc -l
echo "@@THR"
ls "$P/task" 2>/dev/null | wc -l
echo "@@STATUS"
grep -E '^(Name|State|Tgid|Pid|PPid|Uid|Gid|Threads|VmPeak|VmSize|VmRSS|VmSwap|voluntary_ctxt_switches|nonvoluntary_ctxt_switches):' "$P/status" 2>/dev/null
echo "@@SIG"
grep -E '^(SigQ|SigPnd|ShdPnd|SigBlk|SigIgn|SigCgt):' "$P/status" 2>/dev/null
echo "@@MAPS"
head -24 "$P/maps" 2>/dev/null
echo "@@THREADS"
i=0
for t in "$P"/task/*; do
  read -r c < "$t/comm" 2>/dev/null || continue
  echo "${t##*/} $c"
  i=$((i+1))
  [ $i -ge 40 ] && break
done
echo "@@END"
