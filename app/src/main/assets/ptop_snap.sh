#!/system/bin/sh
# ptop-snap v2
# ptop snapshot — single awk process reads /proc and sysfs.
# Field separator: TAB. Sections: @@X. Terminator: @@END.
export LC_ALL=C

awk '
function pct(x) { return x }

BEGIN {
  while ((getline l < "/proc/loadavg") > 0) {
    split(l, a, " "); printf "@@L\t%s\t%s\t%s\n", a[1], a[2], a[3]; break
  }
  while ((getline l < "/proc/uptime") > 0) {
    split(l, a, " "); printf "@@U\t%s\n", a[1]; break
  }
  while ((getline l < "/proc/stat") > 0) {
    if (l ~ /^cpu/) printf "@@C\t%s\n", l; else break
  }
  while ((getline l < "/proc/meminfo") > 0) {
    split(l, a, ": "); v = a[2] + 0
    if (l ~ /^MemTotal:/) mt = v; else if (l ~ /^MemFree:/) mf = v
    else if (l ~ /^MemAvailable:/) ma = v; else if (l ~ /^Buffers:/) mb = v
    else if (l ~ /^Cached:/) mc = v; else if (l ~ /^SwapTotal:/) st = v
    else if (l ~ /^SwapFree:/) sf = v; else if (l ~ /^Dirty:/) md = v
    else if (l ~ /^Shmem:/) ms = v
  }
  printf "@@M\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n", mt, mf, ma, mb, mc, st, sf, md, ms
  while ((getline l < "/proc/net/dev") > 0) {
    n = split(l, a, " ")
    if (n >= 11 && a[1] ~ /:/) { gsub(":", "", a[1]); printf "@@N\t%s\t%s\t%s\n", a[1], a[2], a[10] }
  }
  while ((getline l < "/proc/diskstats") > 0) {
    n = split(l, a, " ")
    if (n < 14) continue
    d = a[3]
    if (d ~ /^(ram|loop|zram|dm-|sr|fd)/) continue
    printf "@@D\t%s\t%s\t%s\t%s\n", d, a[4], a[6], a[10]
  }
  while ((getline l < "/proc/vmstat") > 0) {
    if (l ~ /^pgpgin /) { split(l, a, " "); pg = a[2] }
    else if (l ~ /^pgpgout /) { split(l, a, " "); pq = a[2] }
  }
  printf "@@V\t%d\t%d\n", pg, pq
  while ((getline l < "/proc/uid_io/stats") > 0) {
    split(l, a, " ")
    if (a[1] != "") printf "@@IO\t%s\t%s\t%s\n", a[1], a[2], a[3]
  }
  while ((getline l < "/proc/pressure/cpu") > 0) {
    split(l, a, " "); sub("avg10=", "", a[2]); printf "@@PC\t%s\n", a[2]; break
  }
  while ((getline l < "/proc/pressure/io") > 0) {
    split(l, a, " "); sub("avg10=", "", a[2]); printf "@@PI\t%s\n", a[2]; break
  }
}

FILENAME ~ /\/proc\/[0-9]+\/stat$/ {
  s = $0; i = index(s, "(")
  k = i; while ((j = index(substr(s, k+1), ")")) > 0) k += j
  comm = substr(s, i+1, k-i-1)
  split(substr(s, k+2), r, " ")
  pid = FILENAME; sub("/proc/", "", pid); sub("/stat", "", pid)
  printf "@@P\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n", pid, comm, r[1], r[2], r[12], r[13], r[18], r[22], r[20]
  next
}

FILENAME ~ /thermal_zone[0-9]+\/type$/ {
  z = FILENAME; sub("/type$", "", z); sub(".*thermal_zone", "", z); ty[z] = $0
  next
}

FILENAME ~ /thermal_zone[0-9]+\/temp$/ {
  z = FILENAME; sub("/temp$", "", z); sub(".*thermal_zone", "", z)
  t = $0 + 0; if (t > 1000) t = t / 1000
  printf "@@T\t%s\t%s\t%.1f\n", z, (ty[z] == "" ? "zone" z : ty[z]), t
  next
}

FILENAME ~ /scaling_cur_freq$/ {
  c = FILENAME; sub("/cpufreq/scaling_cur_freq$", "", c); sub(".*cpu", "", c)
  printf "@@F\t%s\t%s\n", c, ($0 + 0)
  next
}

index(FILENAME, "/power_supply/") > 0 {
  n = split(FILENAME, p, "/"); f = p[n]; dev = p[n-1]
  if (f == "type") { if ($0 == "Battery") bat = dev }
  else if (f == "capacity") cap[dev] = $0
  else if (f == "status") sta[dev] = $0
  else if (f == "current_now") cur[dev] = $0
  else if (f == "voltage_now") vol[dev] = $0
  else if (f == "temp") tmp[dev] = $0
  next
}

END {
  if (bat != "")
    printf "@@B\t%s\t%s\t%s\t%s\t%s\n", cap[bat] + 0, sta[bat], cur[bat] + 0, vol[bat] + 0, tmp[bat] + 0
  print "@@END"
}
' /proc/[0-9]*/stat \
  /sys/class/thermal/thermal_zone[0-9]/type \
  /sys/class/thermal/thermal_zone[0-9]/temp \
  /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_cur_freq \
  /sys/class/power_supply/*/type \
  /sys/class/power_supply/*/capacity \
  /sys/class/power_supply/*/status \
  /sys/class/power_supply/*/current_now \
  /sys/class/power_supply/*/voltage_now \
  /sys/class/power_supply/*/temp 2>/dev/null
