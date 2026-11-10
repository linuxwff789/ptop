#!/system/bin/sh
# pid -> uid map, used to resolve process owners.
export LC_ALL=C
exec stat -c '%u %n' /proc/[0-9]* 2>/dev/null
