"""Decimal timestamp bridge; quantities never pass through this conversion."""
from datetime import datetime, timedelta, timezone
from decimal import Decimal, ROUND_HALF_EVEN, localcontext


def decimal_epoch(value: Decimal) -> datetime:
    """Keep legacy seconds/milliseconds threshold and datetime microsecond resolution."""
    if not value.is_finite() or value.adjusted() > 14:
        raise ValueError("Invalid numeric timestamp")
    with localcontext() as context:
        context.prec = max(40, len(value.as_tuple().digits) + 10)
        seconds = value / 1000 if value > Decimal("1e11") else value
        micros = int((seconds * 1000000).to_integral_value(rounding=ROUND_HALF_EVEN))
    return datetime(1970, 1, 1, tzinfo=timezone.utc) + timedelta(microseconds=micros)
