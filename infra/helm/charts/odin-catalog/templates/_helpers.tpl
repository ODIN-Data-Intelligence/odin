{{/*
Expand the name of the chart.
*/}}
{{- define "odin-catalog.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "odin-catalog.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Chart label — name + version.
*/}}
{{- define "odin-catalog.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to every resource.
*/}}
{{- define "odin-catalog.labels" -}}
helm.sh/chart: {{ include "odin-catalog.chart" . }}
{{ include "odin-catalog.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels.
*/}}
{{- define "odin-catalog.selectorLabels" -}}
app.kubernetes.io/name: {{ include "odin-catalog.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Component-specific selector labels.
Usage: {{ include "odin-catalog.componentLabels" (dict "component" "inventory-service" "root" .) }}
*/}}
{{- define "odin-catalog.componentLabels" -}}
app.kubernetes.io/name: {{ .component }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
app.kubernetes.io/part-of: odin-catalog
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
helm.sh/chart: {{ include "odin-catalog.chart" .root }}
{{- end }}

{{/*
ServiceAccount name.
*/}}
{{- define "odin-catalog.serviceAccountName" -}}
{{- include "odin-catalog.fullname" . }}
{{- end }}

{{/*
Kafka bootstrap servers URL.
*/}}
{{- define "odin-catalog.kafkaBootstrap" -}}
{{- printf "%s-kafka:9092" .Release.Name }}
{{- end }}

{{/*
Keycloak issuer URI.
*/}}
{{- define "odin-catalog.keycloakIssuer" -}}
{{- printf "http://%s-keycloak:8180/realms/%s" .Release.Name .Values.keycloak.realm }}
{{- end }}
