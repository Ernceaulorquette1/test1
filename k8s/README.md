# Kubernetes — NutriScan AI

Despliegue en GKE (Google Kubernetes Engine).

```bash
# 1. Namespace y configuración
kubectl apply -f namespace.yaml -f configmap.yaml

# 2. Secretos (nunca commitear secrets.yaml)
cp secrets.example.yaml secrets.yaml   # completar valores reales
kubectl apply -f secrets.yaml

# 3. Datos y cache (staging; en prod usar Cloud SQL / Memorystore)
kubectl apply -f postgres.yaml -f redis.yaml

# 4. API + autoscaling + HTTPS
kubectl apply -f api-deployment.yaml -f hpa.yaml -f ingress.yaml

kubectl -n nutriscan get pods -w
```

Escala: 3–30 réplicas por HPA (CPU 70%), probes de liveness/readiness,
rolling updates sin downtime, contenedor no-root con FS de solo lectura.
