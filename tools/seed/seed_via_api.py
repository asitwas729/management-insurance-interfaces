import json
import sys
import time
from dataclasses import dataclass

import requests


@dataclass(frozen=True)
class AuthTokens:
    access_token: str
    refresh_token: str | None


def login(base_url: str, username: str, password: str) -> AuthTokens:
    r = requests.post(
        f"{base_url}/api/v1/auth/login",
        json={"username": username, "password": password},
        timeout=10,
    )
    r.raise_for_status()
    data = r.json()
    return AuthTokens(access_token=data["accessToken"], refresh_token=data.get("refreshToken"))


def execute_interface(base_url: str, token: str, interface_code: str, idempotency_key: str, payload: dict) -> dict:
    r = requests.post(
        f"{base_url}/api/v1/interfaces/{interface_code}/execute",
        headers={"Authorization": f"Bearer {token}"},
        json={"idempotencyKey": idempotency_key, "payload": payload},
        timeout=20,
    )
    if r.status_code >= 400:
        try:
            return {"status": r.status_code, "body": r.json()}
        except Exception:
            return {"status": r.status_code, "body": r.text}
    return {"status": r.status_code, "body": r.json()}


def main() -> int:
    base_url = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
    interface_code = sys.argv[2] if len(sys.argv) > 2 else "FSS_POLICY_REPORT"
    count = int(sys.argv[3]) if len(sys.argv) > 3 else 20

    tokens = login(base_url, "operator1", "operator123")

    results = []
    for i in range(count):
        idem = f"API-SEED-{interface_code}-{int(time.time())}-{i:04d}"
        payload = {
            "policyNo": f"P{time.strftime('%Y%m%d')}{i:06d}",
            "eventType": "NEW_CONTRACT" if i % 2 == 0 else "CLAIM",
            "rrn": "******-*******",
            "cardNo": "************1111",
        }
        res = execute_interface(base_url, tokens.access_token, interface_code, idem, payload)
        results.append({"idempotencyKey": idem, **res})
        time.sleep(0.05)

    print(json.dumps({"baseUrl": base_url, "interfaceCode": interface_code, "count": count, "results": results}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

