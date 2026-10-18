from fastapi import APIRouter

from . import admin, auth, billing, catalog, me, predictions, system, user_data

api_router = APIRouter()
for r in (system.router, auth.router, me.router, catalog.router, predictions.router, user_data.router, billing.router, admin.router):
    api_router.include_router(r)
