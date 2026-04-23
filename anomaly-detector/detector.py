"""
InterfaceHub 이상탐지 배치.

Prometheus에서 30초마다 메트릭을 수집하고 Z-score 기반으로
실패율·지연시간 급증을 탐지한다.
"""
from __future__ import annotations

import collections
import logging
import os
import time
from typing import Optional

import numpy as np
import requests
from scipy import stats

from alerting import AlertManager, AnomalyEvent

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger("detector")

PROMETHEUS_URL = os.getenv("PROMETHEUS_URL", "http://localhost:9090")
POLL_INTERVAL = int(os.getenv("POLL_INTERVAL_SECONDS", "30"))
ZSCORE_THRESHOLD = float(os.getenv("ZSCORE_THRESHOLD", "3.0"))
SUPPRESS_SECONDS = int(os.getenv("ALERT_SUPPRESS_SECONDS", "300"))
WINDOW_SIZE = 30  # 슬라이딩 윈도우 샘플 수

# interfaceCode × metric → deque of float samples
_windows: dict[str, collections.deque] = collections.defaultdict(
    lambda: collections.deque(maxlen=WINDOW_SIZE)
)


def query_prometheus(promql: str) -> list[dict]:
    try:
        resp = requests.get(
            f"{PROMETHEUS_URL}/api/v1/query",
            params={"query": promql},
            timeout=10,
        )
        resp.raise_for_status()
        data = resp.json()
        return data.get("data", {}).get("result", [])
    except Exception as exc:
        logger.warning("Prometheus query failed (%s): %s", promql[:60], exc)
        return []


def fetch_failure_rates() -> dict[str, float]:
    """인터페이스별 5분 실패율 (0~1)."""
    success = query_prometheus(
        'rate(execution_count_total{outcome="success"}[5m])'
    )
    failure = query_prometheus(
        'rate(execution_count_total{outcome="failure"}[5m])'
    )

    success_map: dict[str, float] = {
        r["metric"].get("interfaceCode", ""): float(r["value"][1])
        for r in success
    }
    failure_map: dict[str, float] = {
        r["metric"].get("interfaceCode", ""): float(r["value"][1])
        for r in failure
    }

    result: dict[str, float] = {}
    all_codes = set(success_map) | set(failure_map)
    for code in all_codes:
        s = success_map.get(code, 0.0)
        f = failure_map.get(code, 0.0)
        total = s + f
        result[code] = f / total if total > 0 else 0.0
    return result


def fetch_latencies() -> dict[str, float]:
    """인터페이스별 5분 평균 지연시간 (초)."""
    results = query_prometheus(
        "rate(execution_latency_seconds_sum[5m])"
        " / rate(execution_latency_seconds_count[5m])"
    )
    return {
        r["metric"].get("interfaceCode", ""): float(r["value"][1])
        for r in results
        if r["value"][1] != "NaN"
    }


def detect(
    code: str,
    metric: str,
    value: float,
    alert_manager: AlertManager,
) -> None:
    key = f"{code}:{metric}"
    window = _windows[key]
    window.append(value)

    if len(window) < 10:
        return  # 샘플 부족

    arr = np.array(window)
    mean = float(arr.mean())
    std = float(arr.std())

    if std < 1e-9:
        return  # 분산 없음 — 안정 상태

    z = float(stats.zscore(arr)[-1])

    if abs(z) >= ZSCORE_THRESHOLD:
        alert_manager.send(
            AnomalyEvent(
                interface_code=code,
                metric=metric,
                current_value=value,
                mean=mean,
                std=std,
                z_score=z,
            )
        )


def run() -> None:
    alert_manager = AlertManager(suppress_seconds=SUPPRESS_SECONDS)
    logger.info(
        "Anomaly detector started — poll=%ds zscore=%.1f suppress=%ds",
        POLL_INTERVAL,
        ZSCORE_THRESHOLD,
        SUPPRESS_SECONDS,
    )

    while True:
        failure_rates = fetch_failure_rates()
        latencies = fetch_latencies()

        for code, rate in failure_rates.items():
            detect(code, "failure_rate", rate, alert_manager)

        for code, latency in latencies.items():
            detect(code, "latency_seconds", latency, alert_manager)

        logger.info(
            "Poll complete — interfaces=%d failure_samples=%d latency_samples=%d",
            len(set(failure_rates) | set(latencies)),
            len(failure_rates),
            len(latencies),
        )
        time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    run()
