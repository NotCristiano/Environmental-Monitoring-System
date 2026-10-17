JDK25 ?= /usr/lib/jvm/java-25-temurin
PRESET ?= steady
COUNT ?= 1

.PHONY: up sources stop-sources down status logs

up:
	JAVA_HOME=$(JDK25) mvn package
	docker build --build-arg MODULE=ingestion -t metriclab-ingestion:local .
	docker build --build-arg MODULE=source -t metriclab-source:local .
	docker save metriclab-ingestion:local metriclab-source:local -o /tmp/metriclab-images.tar
	sudo k3s ctr -n k8s.io images import /tmp/metriclab-images.tar
	kubectl apply -f k8s/metriclab.yaml
	kubectl delete deployment/source --ignore-not-found
	kubectl rollout restart deployment/ingestion
	kubectl rollout status deployment/ingestion

sources:
	@test -f "k8s/presets/$(PRESET).yaml" || { echo "Unknown preset: $(PRESET)" >&2; exit 1; }
	@case "$(COUNT)" in ''|*[!0-9]*|0) echo "COUNT must be a positive integer" >&2; exit 1;; esac
	@kubectl rollout status deployment/ingestion
	@i=0; while [ $$i -lt "$(COUNT)" ]; do kubectl create -f "k8s/presets/$(PRESET).yaml" || exit; i=$$((i + 1)); done

stop-sources:
	kubectl delete jobs -l app=metriclab-source --ignore-not-found
	kubectl delete deployment/source --ignore-not-found

down: stop-sources
	kubectl delete -f k8s/metriclab.yaml

status:
	watch -n 2 -d 'sh scripts/status.sh'

logs:
	kubectl logs -f -l app=ingestion --prefix --max-log-requests=4
