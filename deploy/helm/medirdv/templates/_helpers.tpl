{{- define "medirdv.labels" -}}
app.kubernetes.io/part-of: medirdv
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}

{{- define "medirdv.image" -}}
{{- printf "%s/medirdv-%s:%s" .root.Values.image.registry .name (toString (default .root.Chart.AppVersion .root.Values.image.tag)) -}}
{{- end -}}

{{/* Hardened container security context shared by every workload. */}}
{{- define "medirdv.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: [ALL]
{{- end -}}

{{- define "medirdv.keycloakUrl" -}}
{{- $issuer := .Values.external.keycloak.issuerUri -}}
{{- default (regexReplaceAll "/realms/[^/]+/?$" $issuer "") .Values.web.keycloakUrl -}}
{{- end -}}
