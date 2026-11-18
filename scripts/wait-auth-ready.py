"""Readiness check: login is side-effect-free apart from issuing a short-lived JWT."""
import importlib.util
from pathlib import Path
s=importlib.util.spec_from_file_location("auth_test",Path(__file__).with_name("auth-test.py")); m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
try:
    status,data=m.call("POST","/api/auth/login",{"username":"buyer","password":m.env["BUYER_PASSWORD"]})
    raise SystemExit(0 if status==200 and data.get("token") else 1)
except Exception:raise SystemExit(1)
