"""Fill missing week09 secrets in an existing .env, without printing or rotating them."""
import base64,secrets
from pathlib import Path
p=Path(__file__).resolve().parents[1]/".env"
if not p.exists():raise SystemExit("Copy .env.example to .env and configure DB/Nacos credentials first")
text=p.read_text(encoding="utf-8-sig")
lines=text.splitlines(); existing={line.split("=",1)[0]:line.split("=",1)[1] for line in lines if "=" in line and not line.lstrip().startswith("#")}
for key in ["JWT_SECRET","BUYER_PASSWORD","BUYER2_PASSWORD","ADMIN_PASSWORD"]:
    if existing.get(key,"").strip():continue
    value=base64.b64encode(secrets.token_bytes(64)).decode() if key=="JWT_SECRET" else secrets.token_urlsafe(20)
    lines=[line for line in lines if not line.startswith(key+"=")]
    lines.append(key+"="+value)
p.write_text("\n".join(lines)+"\n",encoding="utf-8")
print("Local week09 secrets ready. Values remain in ignored .env; existing values preserved.")
