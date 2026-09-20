# Purrrfolio observability

Import `dashboard.json` into Grafana and select the Prometheus-compatible
datasource and the `purrrfolio_prod` scrape job. The layout follows the
operating pattern used by Frontline Nations; metric definitions are
Purrrfolio-specific.

Prometheus must scrape `GET /actuator/prometheus` on management port `9090`
of the application container. The public game and webhook server stays on
port `8080`, so metrics are not routed through the public domain. The
dashboard combines two kinds of measurements:

- event counters for commands, inline-button actions, Stars funnel stages,
  pack openings, opened cards by rarity/source, free-card claims, craft melts,
  trades and market offers;
- database-backed gauges refreshed once per minute for player activity,
  registration attribution, pack grants/opened counts by source, Stars payments, craft
  point bank, trade pool depth and active market listings.

Database gauges stay correct after restarts. Registration views use exact
database counts. Plain `/start` is labelled `direct`; valid `/start` payloads
(deep links like `t.me/<bot>?start=blog`) are normalized to lowercase
campaign codes (`[a-z0-9_-]{1,64}`, anything else becomes `other`). At most
24 source series are exposed per period, the remainder is grouped as `other`.

The dashboard uses UTC. Event labels stay bounded: never Telegram IDs,
nicknames, raw payloads, payment charge IDs or full callback data.

Recommended scrape configuration (VictoriaMetrics `vmagent.yml` style):

```yaml
scrape_configs:
  - job_name: purrrfolio_prod
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: [purrrfolio-prod-app:9090]
```

Keep the metrics endpoint on the private Docker network. Do not expose
production metrics publicly.
