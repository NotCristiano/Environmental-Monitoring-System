#!/bin/sh

pods=$(kubectl get pods -l app=ingestion -o jsonpath='{range .items[*]}{.metadata.namespace}{" "}{.metadata.name}{" "}{.spec.containers[?(@.name=="ingestion")].resources.limits.cpu}{" "}{.spec.containers[?(@.name=="ingestion")].resources.limits.memory}{"\n"}{end}') || exit 1
metrics=$(kubectl top pods -l app=ingestion --no-headers 2>/dev/null) || metrics=''

printf '%-38s %10s %10s %8s\n' 'NAME' 'CPU %LIMIT' 'MEM %LIMIT' 'SOURCES'
printf '%s\n' "$pods" | while read -r namespace pod cpu_limit memory_limit; do
	[ -n "$pod" ] || continue
	usage=$(printf '%s\n' "$metrics" | awk -v pod="$pod" '$1 == pod { print $2, $3; exit }')
	set -- $usage
	if [ -n "$usage" ] && [ -n "$cpu_limit" ] && [ -n "$memory_limit" ]; then
		percentages=$(awk -v cpu="$1" -v memory="$2" -v cpu_limit="$cpu_limit" -v memory_limit="$memory_limit" '
			function cpu_m(value) {
				if (value ~ /m$/) { sub(/m$/, "", value); return value + 0 }
				return value * 1000
			}
			function memory_ki(value) {
				if (value ~ /Ki$/) { sub(/Ki$/, "", value); return value + 0 }
				if (value ~ /Mi$/) { sub(/Mi$/, "", value); return value * 1024 }
				if (value ~ /Gi$/) { sub(/Gi$/, "", value); return value * 1048576 }
				return value / 1024
			}
			BEGIN {
				cpu_percent = 100 * cpu_m(cpu) / cpu_m(cpu_limit)
				memory_percent = 100 * memory_ki(memory) / memory_ki(memory_limit)
				printf "%.0f%% %.0f%%", (cpu_percent > 100 ? 100 : cpu_percent), (memory_percent > 100 ? 100 : memory_percent)
			}
		')
		set -- $percentages
		cpu_percent=$1 memory_percent=$2
	else
		cpu_percent='?' memory_percent='?'
	fi
	count=$(kubectl get --raw "/api/v1/namespaces/$namespace/pods/$pod:8080/proxy/active-sources" 2>/dev/null) || count='?'
	printf '%-38s %10s %10s %8s\n' "$pod" "$cpu_percent" "$memory_percent" "$count"
done
