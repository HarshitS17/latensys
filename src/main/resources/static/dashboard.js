/* ================= GLOBAL CHART REFERENCES ================= */

let endpointChart = null;
let rpmChart = null;
let blockedChart = null;
let sparklineChart = null;
let refreshInFlight = false;
let refreshPending = false;


/* ================= SAFE FETCH ================= */

async function safeFetch(url) {
    try {
        const res = await fetch(url);
        if (res.status === 401) {
            window.location.assign("/login?expired");
            return null;
        }
        if (!res.ok) throw new Error("Network error");
        return await res.json();
    } catch (err) {
        console.error("Fetch failed:", err);
        return null;
    }
}


/* ================= GLOBAL METRICS ================= */

function loadGlobalMetrics(data) {
    if (!data) {
        document.getElementById("totalRequests").innerText = "Unavailable";
        document.getElementById("avgResponse").innerText = "Unavailable";
        return;
    }

    document.getElementById("totalRequests").innerText =
        data.totalRequests;

    document.getElementById("avgResponse").innerText =
        Math.round(data.averageResponseTime) + " ms";
}


/* ================= ERROR RATE ================= */

function loadErrorRate(data) {
    if (!data) {
        document.getElementById("errorRate").innerText = "Unavailable";
        document.getElementById("errorBreakdown").innerText = "Application and enforcement counts unavailable";
        return;
    }

    // ✅ Number() guard ensures toFixed() works even if server returns a string
    const rate = Number(data.errorRate).toFixed(2);
    const el = document.getElementById("errorRate");

    el.innerText = rate + "%";
    document.getElementById("errorBreakdown").innerText =
        `${data.clientErrors + data.serverErrors} application errors · ${data.blockedRequests} rejected · ${data.unavailableRequests} enforcement failures`;

    if (rate > 5) el.style.color = "#ef4444";
    else if (rate > 2) el.style.color = "#facc15";
    else el.style.color = "#22c55e";
}


/* ================= RATE LIMIT STATS ================= */

function loadRateLimitStats(data, rpmData, blockedData) {
    if (!data) {
        document.getElementById("rateEnabled").innerText = "UNAVAILABLE";
        document.getElementById("rateEnabled").className = "status disabled";
        document.getElementById("rateLimit").innerText = "Unavailable";
        document.getElementById("blockedRequests").innerText = "Unavailable";
        document.getElementById("blockPercentage").innerText = "Unavailable";
        document.getElementById("currentRPS").innerText = "Unavailable";
        document.getElementById("blockedLastMin").innerText = "Unavailable";
        document.getElementById("policyList").innerText = "Policies unavailable";
        return;
    }

    const statusEl = document.getElementById("rateEnabled");

    statusEl.innerText = data.enabled ? "ENABLED" : "DISABLED";
    statusEl.className = data.enabled
        ? "status enabled"
        : "status disabled";

    document.getElementById("rateLimit").innerText =
        `${data.limit} requests / ${data.windowSeconds}s`;
    const overrides = Object.entries(data.overrides || {}).sort(([a], [b]) => a.localeCompare(b));
    document.getElementById("policyList").innerText = overrides.length
        ? overrides.map(([path, policy]) => `${path}: ${policy.maxRequests} / ${policy.windowSeconds}s`).join(" · ")
        : "No endpoint overrides";

    const blocked = data.blockedRequests || 0;
    // ✅ P0 Bug #1 FIX: removed getElementById("blockedMetric") — element does not exist in HTML
    document.getElementById("blockedRequests").innerText = blocked;

    // Calculate block percentage
    const blockPercentage = Number(data.blockPercentage).toFixed(1);

    const percentEl = document.getElementById("blockPercentage");
    if (percentEl) {
        percentEl.innerText = `${blockPercentage}% of retained traffic`;
    }

    // Current requests per second (estimate)
    const rpsEl = document.getElementById("currentRPS");
    if (rpsEl) {
        if (rpmData && rpmData.length > 1) {
            const lastCompletedMinute = rpmData[rpmData.length - 2];
            const rps = Math.round(lastCompletedMinute / 60);
            rpsEl.innerText = rps;
        } else {
            rpsEl.innerText = "Unavailable";
        }
    }

    // Blocked in last minute
    const blockedLastMinEl = document.getElementById("blockedLastMin");
    if (blockedLastMinEl) {
        if (blockedData && blockedData.length > 0) {
            blockedLastMinEl.innerText = blockedData[blockedData.length - 1];
        } else {
            blockedLastMinEl.innerText = "Unavailable";
        }
    }
}

/* ================= SPARKLINE CHART ================= */

function loadSparklineChart(data) {
    if (!data) {
        if (sparklineChart) {
            sparklineChart.data.datasets[0].data = [];
            sparklineChart.update('none');
        }
        return;
    }

    const ctx = document.getElementById("blockedSparkline");
    if (!ctx) return;

    if (!sparklineChart) {
        sparklineChart = new Chart(ctx, {
            type: "line",
            data: {
                labels: Array.from({ length: data.length }, (_, i) => ''),
                datasets: [{
                    data: data,
                    borderColor: "#ef4444",
                    backgroundColor: "rgba(239,68,68,0.2)",
                    borderWidth: 2,
                    tension: 0.4,
                    fill: true,
                    pointRadius: 0,
                    pointHoverRadius: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: 300 },
                plugins: {
                    legend: { display: false },
                    tooltip: { enabled: false }
                },
                scales: {
                    x: { display: false },
                    y: {
                        display: false,
                        beginAtZero: true
                    }
                },
                interaction: {
                    mode: null
                }
            }
        });
    } else {
        sparklineChart.data.datasets[0].data = data;
        sparklineChart.update('none');
    }
}


/* ================= ENDPOINT P95 CHART ================= */

function loadEndpointChart(data, windowValue) {
    if (!data) {
        if (endpointChart) {
            endpointChart.data.labels = [];
            endpointChart.data.datasets[0].data = [];
            endpointChart.update();
        }
        return;
    }

    const labels = data.map(d => d.uri);
    const values = data.map(d => d.p95Latency);

    const ctx = document.getElementById("endpointChart");
    if (!ctx) return;

    if (!endpointChart) {

        endpointChart = new Chart(ctx, {
            type: "bar",
            data: {
                labels: labels,
                datasets: [{
                    label: `P95 (${windowValue}m)`,
                    data: values,
                    backgroundColor: "#22c55e",
                    borderRadius: 6
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: 600 },
                plugins: {
                    legend: { display: false }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            callback: v => v + " ms"
                        }
                    }
                }
            }
        });

    } else {
        endpointChart.data.labels = labels;
        endpointChart.data.datasets[0].data = values;
        endpointChart.data.datasets[0].label =
            `P95 (${windowValue}m)`;
        endpointChart.update();
    }
}


/* ================= RPM CHART ================= */

function loadRPMChart(data) {
    if (!data) {
        if (rpmChart) {
            rpmChart.data.labels = [];
            rpmChart.data.datasets[0].data = [];
            rpmChart.update();
        }
        return;
    }

    const ctx = document.getElementById("rpmChart");
    if (!ctx) return;

    const labels = Array.from({ length: data.length }, (_, i) =>
        i === data.length - 1 ? "Now (partial)" : `${i - data.length + 1}m`
    );

    if (!rpmChart) {

        rpmChart = new Chart(ctx, {
            type: "line",
            data: {
                labels: labels,
                datasets: [{
                    label: "Requests Per Minute",
                    data: data,
                    borderColor: "#3b82f6",
                    backgroundColor: "rgba(59,130,246,0.2)",
                    tension: 0.4,
                    fill: true
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: 600 },
                plugins: { legend: { display: false } },
                scales: {
                    y: { beginAtZero: true }
                }
            }
        });

    } else {
        rpmChart.data.datasets[0].data = data;
        rpmChart.update();
    }
}


/* ================= BLOCKED TREND CHART ================= */

function loadBlockedChart(data) {
    if (!data) {
        if (blockedChart) {
            blockedChart.data.labels = [];
            blockedChart.data.datasets[0].data = [];
            blockedChart.update();
        }
        return;
    }

    const ctx = document.getElementById("blockedChart");
    if (!ctx) return;

    const labels = Array.from({ length: data.length }, (_, i) =>
        i === data.length - 1 ? "Now (partial)" : `${i - data.length + 1}m`
    );

    if (!blockedChart) {

        blockedChart = new Chart(ctx, {
            type: "line",
            data: {
                labels: labels,
                datasets: [{
                    label: "Blocked Requests",
                    data: data,
                    borderColor: "#ef4444",
                    backgroundColor: "rgba(239,68,68,0.2)",
                    tension: 0.4,
                    fill: true
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: 600 },
                plugins: { legend: { display: false } },
                scales: {
                    y: { beginAtZero: true }
                }
            }
        });

    } else {
        blockedChart.data.datasets[0].data = data;
        blockedChart.update();
    }
}


/* ================= INIT ================= */

async function loadAll() {
    if (refreshInFlight) {
        refreshPending = true;
        return;
    }
    refreshInFlight = true;
    const windowValue = document.getElementById("windowSelect")?.value || "5";
    try {
        const [metrics, rateLimit, endpoints, rpm, blocked] = await Promise.all([
            safeFetch("/monitor/metrics"),
            safeFetch("/monitor/rate-limit"),
            safeFetch(`/monitor/endpoints?window=${windowValue}`),
            safeFetch("/monitor/rpm"),
            safeFetch("/monitor/blocked-trend")
        ]);
        loadGlobalMetrics(metrics);
        loadErrorRate(metrics);
        loadRateLimitStats(rateLimit, rpm, blocked);
        loadEndpointChart(endpoints, windowValue);
        loadRPMChart(rpm);
        loadBlockedChart(blocked);
        loadSparklineChart(blocked);
    } finally {
        refreshInFlight = false;
        if (refreshPending) {
            refreshPending = false;
            loadAll();
        }
    }
}


document.addEventListener("DOMContentLoaded", function () {

    const logoutForm = document.getElementById("logoutForm");
    if (logoutForm) {
        logoutForm.addEventListener("submit", async event => {
            event.preventDefault();
            const csrf = await safeFetch("/auth/csrf");
            if (csrf?.token) {
                document.getElementById("logoutCsrf").value = csrf.token;
                logoutForm.submit();
            }
        });
    }

    const windowSelect =
        document.getElementById("windowSelect");

    if (windowSelect) {
        windowSelect.addEventListener(
            "change",
            loadAll
        );
    }

    loadAll();
    setInterval(loadAll, 5000);

});
