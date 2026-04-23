import logging
import os
import time
from dataclasses import dataclass, field
from typing import Optional

logger = logging.getLogger(__name__)


@dataclass
class AnomalyEvent:
    interface_code: str
    metric: str          # "failure_rate" | "latency_seconds"
    current_value: float
    mean: float
    std: float
    z_score: float
    detected_at: float = field(default_factory=time.time)

    def __str__(self) -> str:
        return (
            f"[ANOMALY] interfaceCode={self.interface_code} "
            f"metric={self.metric} "
            f"value={self.current_value:.4f} "
            f"mean={self.mean:.4f} std={self.std:.4f} "
            f"z_score={self.z_score:.2f}"
        )


class AlertManager:
    """중복 경보를 suppress_seconds 동안 억제한다."""

    def __init__(self, suppress_seconds: int = 300) -> None:
        self._suppress_seconds = suppress_seconds
        self._last_alert: dict[str, float] = {}

    def send(self, event: AnomalyEvent) -> None:
        key = f"{event.interface_code}:{event.metric}"
        now = time.time()
        last = self._last_alert.get(key, 0.0)

        if now - last < self._suppress_seconds:
            return

        self._last_alert[key] = now
        logger.warning(str(event))
        self._send_webhook(event)

    def _send_webhook(self, event: AnomalyEvent) -> None:
        webhook_url = os.getenv("ALERT_WEBHOOK_URL")
        if not webhook_url:
            return

        import requests  # lazy import — only needed when webhook is configured

        payload = {
            "interfaceCode": event.interface_code,
            "metric": event.metric,
            "currentValue": event.current_value,
            "mean": event.mean,
            "std": event.std,
            "zScore": event.z_score,
        }
        try:
            requests.post(webhook_url, json=payload, timeout=5)
        except Exception as exc:
            logger.error("Webhook delivery failed: %s", exc)
