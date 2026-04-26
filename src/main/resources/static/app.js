const $ = (id) => document.getElementById(id);

let accessToken = "";
let selectedInterfaceCode = "";
let selectedExecutionId = "";
let cachedInterfaces = [];
let cachedHistories = [];
let cachedRetryTasks = [];
let cachedHistorySearch = { page: 0, size: 50, totalPages: 0, totalElements: 0, criteria: null, content: [] };
let cachedAuditSearch = { page: 0, size: 20, totalPages: 0, totalElements: 0, criteria: null, content: [] };
let cachedApiLogs = [];
let pendingApiLogs = [];
let pollTimer = null;
let polling = false;
let pollIntervalMs = 3000;
let globalSearchSeq = 0;
let lastDashboardSummarySnapshot = null;
let dashboardWindowHours = 24;
let dashExecDonutChart = null;
let dashTopFailBarChart = null;
let dashLatencyTrendChart = null;
let hsTimelineChart = null;
let hsStatusDonutChart = null;

const STATUS_COLORS = {
  SUCCESS: "#24a36b",
  FAILED: "#d35151",
  TIMEOUT: "#d79a2c",
  CANCELLED: "#8998b0",
  RUNNING: "#8b5cf6",
};
let dashRetryDonutChart = null;
let dashDlqReplayDonutChart = null;
let dashSlaBarChart = null;
let ifLatencyChart = null;
let ifStatusDonutChart = null;
let currentUsername = "";

let dlqState = { page: 0, size: 20, totalPages: 0, totalElements: 0, interfaceCode: "" };
let dlqReplayState = { page: 0, size: 20, totalPages: 0, totalElements: 0, status: "", from: "", to: "" };

function bindClick(id, handler) {
  const element = $(id);
  if (element) {
    element.addEventListener("click", handler);
  }
}

function baseUrl() {
  const raw = $("baseUrl")?.value?.trim() || "";
  const normalized = raw.replace(/\/$/, "");
  if (normalized) {
    return normalized;
  }
  return window.location.origin;
}

function nowText() {
  return formatDateTime(new Date().toISOString());
}

function nowIso() {
  return new Date().toISOString();
}

function setText(id, text) {
  const element = $(id);
  if (element) {
    element.textContent = text;
  }
}

function setValue(id, value) {
  const element = $(id);
  if (!element) {
    return;
  }
  element.value = value == null ? "" : String(value);
}

function setFieldText(id, text) {
  const element = $(id);
  if (!element) return;
  if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) {
    element.value = text;
    return;
  }
  element.textContent = text;
}

function debounce(fn, delayMs) {
  let timer = null;
  return (...args) => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delayMs);
  };
}

function showOutput(payload) {
  const output = $("output");
  if (!output) {
    return;
  }
  output.textContent = typeof payload === "string" ? payload : JSON.stringify(payload, null, 2);
}

function showToast(message, type = "success") {
  const toast = $("toast");
  if (!toast) {
    return;
  }
  toast.textContent = message;
  toast.className = `toast ${type}`;
  toast.classList.remove("hidden");
  setTimeout(() => toast.classList.add("hidden"), 2600);
}

function getErrorMessage(error) {
  if (!error) {
    return "알 수 없는 오류";
  }
  if (typeof error === "string") {
    return error;
  }
  if (typeof error.status === "number") {
    const statusText = error.statusText ? ` ${error.statusText}` : "";
    const detail = error.message ? `: ${error.message}` : "";
    return `HTTP ${error.status}${statusText}${detail}`;
  }
  return error.message || error.error || error.code || "알 수 없는 오류";
}

function formatDateTime(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const dd = String(date.getDate()).padStart(2, "0");
  const hh = String(date.getHours()).padStart(2, "0");
  const mi = String(date.getMinutes()).padStart(2, "0");
  const ss = String(date.getSeconds()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}:${ss}`;
}

function parseBody(text) {
  if (!text || !text.trim()) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch (_error) {
    return { message: text };
  }
}

function parseJsonInput(id, fallback = {}) {
  const raw = $(id)?.value?.trim();
  if (!raw) {
    return fallback;
  }
  return JSON.parse(raw);
}

function prettyJson(value) {
  if (value == null || value === "") {
    return "";
  }
  if (typeof value === "string") {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch (_error) {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
}

function statusClass(status) {
  if (status === "SUCCESS") return "success";
  if (status === "RUNNING") return "running";
  if (status === "FAILED" || status === "TIMEOUT" || status === "CANCELLED") return "fail";
  return "pending";
}

function statusText(status) {
  if (status === "SUCCESS") return "성공";
  if (status === "RUNNING") return "진행중";
  if (status === "FAILED") return "실패";
  if (status === "TIMEOUT") return "타임아웃";
  if (status === "CANCELLED") return "취소";
  return status || "-";
}

function classifyErrorCategory(errorCode = "", errorMessage = "") {
  const clue = `${errorCode} ${errorMessage}`.toUpperCase();
  if (/TIMEOUT|DNS|SSL|CONNECT|SOCKET|READ_TIMEOUT|CONNECTION/.test(clue)) return "NETWORK";
  if (/AUTH|UNAUTHORIZED|FORBIDDEN|TOKEN|CERTIFICATE/.test(clue)) return "AUTH";
  if (/SCHEMA|FORMAT|MAPPING|VALIDATION|PAYLOAD|REQUIRED/.test(clue)) return "DATA";
  if (/DUPLICATE|ALREADY|NOT_ELIGIBLE|NO_CONTRACT|BUSINESS/.test(clue)) return "BUSINESS";
  if (/RATE|CIRCUIT|REPLAY|WINDOW/.test(clue)) return "POLICY";
  return "SYSTEM";
}

function categoryText(category) {
  return {
    NETWORK: "네트워크 오류",
    AUTH: "인증/권한 오류",
    DATA: "데이터 오류",
    BUSINESS: "업무 오류",
    SYSTEM: "시스템 오류",
    POLICY: "운영 정책 오류",
  }[category] || "미분류";
}

function businessReason(errorCode, category) {
  if (!errorCode) return "오류코드 없음: 로그 상세 확인 필요";
  const code = errorCode.toUpperCase();
  if (category === "NETWORK") return "외부 연계 구간에서 응답 지연 또는 연결 실패가 발생했습니다.";
  if (category === "AUTH") return "인증 토큰/권한/인증서 조건을 충족하지 못했습니다.";
  if (category === "DATA") return "요청 데이터 구조 또는 필수값 검증에서 실패했습니다.";
  if (category === "BUSINESS") return "업무 규칙 위반(대상 없음/중복 처리 등)으로 거절되었습니다.";
  if (category === "POLICY") return "운영 정책(rate limit, 재처리 제한, 실행창 제한)에 걸렸습니다.";
  if (code.includes("DB") || code.includes("MQ")) return "내부 인프라(DB/MQ/스토리지) 오류 가능성이 높습니다.";
  return "내부 시스템 오류로 판단됩니다. 상세 로그를 확인해 원인 시스템을 특정하세요.";
}

function parseIso(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function relativeTime(value) {
  const date = parseIso(value);
  if (!date) return "-";
  const diffMs = Date.now() - date.getTime();
  if (diffMs < 0) return "방금";
  const sec = Math.floor(diffMs / 1000);
  if (sec < 60) return `${sec}초 전`;
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}분 전`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}시간 전`;
  const day = Math.floor(hour / 24);
  return `${day}일 전`;
}

function withinTimeFilter(startedAt, timeFilter) {
  if (timeFilter === "ALL") return true;
  const started = parseIso(startedAt);
  if (!started) return false;
  const now = Date.now();
  const ageMs = now - started.getTime();
  const limit = {
    "1H": 60 * 60 * 1000,
    "6H": 6 * 60 * 60 * 1000,
    "24H": 24 * 60 * 60 * 1000,
  }[timeFilter];
  return ageMs <= (limit || Number.MAX_SAFE_INTEGER);
}

function getFilteredHistories() {
  const timeFilter = $("failureTimeFilter")?.value || "ALL";
  const statusFilter = $("failureStatusFilter")?.value || "ALL";
  const typeFilter = $("failureTypeFilter")?.value || "ALL";

  return cachedHistories.filter((history) => {
    if (!withinTimeFilter(history.startedAt, timeFilter)) return false;
    if (statusFilter !== "ALL" && history.status !== statusFilter) return false;
    if (typeFilter !== "ALL") {
      const category = classifyErrorCategory(history.errorCode, history.errorMessage);
      if (category !== typeFilter) return false;
    }
    return true;
  });
}

function getSelectedHistoryIds() {
  return Array.from(document.querySelectorAll(".history-select:checked")).map((input) => input.value);
}

function getCurrentHistory() {
  if (!selectedExecutionId) {
    return getFilteredHistories()[0] || null;
  }
  return cachedHistories.find((item) => item.executionId === selectedExecutionId) || null;
}

function syncSelectAllState() {
  const rows = Array.from(document.querySelectorAll(".history-select"));
  const selectAll = $("selectAllFailures");
  if (!selectAll || rows.length === 0) {
    if (selectAll) selectAll.checked = false;
    return;
  }
  const checked = rows.filter((row) => row.checked).length;
  selectAll.checked = checked > 0 && checked === rows.length;
}

function updateLastUpdated() {
  setText("lastUpdatedAt", `마지막 갱신: ${nowText()}`);
}

function truncateText(value, maxLen = 12000) {
  if (value == null) return "";
  const text = typeof value === "string" ? value : JSON.stringify(value);
  if (text.length <= maxLen) return text;
  return `${text.slice(0, maxLen)}\n... (truncated ${text.length - maxLen} chars)`;
}

function renderApiLogBox(entries) {
  const box = $("apiLogBox");
  if (!box) return;
  const list = entries || [];
  if (list.length === 0) {
    box.textContent = "API 로그가 없습니다.";
    return;
  }
  box.textContent = list.map((e) => {
    const at = e.occurredAt || e.createdAt || nowText();
    const status = e.responseStatus == null ? "-" : String(e.responseStatus);
    const duration = e.durationMs == null ? "-" : `${e.durationMs}ms`;
    const err = e.errorMessage ? `\n  error=${e.errorMessage}` : "";
    return `[${at}] ${e.method} ${e.path} status=${status} duration=${duration}${err}`;
  }).join("\n");
}

function setNavBadge(id, count) {
  const el = $(id);
  if (!el) return;
  const n = Number(count || 0);
  if (!n) {
    el.classList.add("hidden");
    el.textContent = "0";
    return;
  }
  el.classList.remove("hidden");
  el.textContent = String(n);
}

async function sendApiLogToServer(entry) {
  if (!accessToken) {
    return false;
  }
  try {
    const response = await fetch(`${baseUrl()}/api/v1/debug-logs`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify(entry),
    });
    if (!response.ok) {
      return false;
    }
    return true;
  } catch (_error) {
    return false;
  }
}

// async function flushPendingApiLogs() {
//   if (!accessToken) return;
//   if (pendingApiLogs.length === 0) return;
//   const pending = [...pendingApiLogs];
//   pendingApiLogs = [];
//   for (const entry of pending) {
//     const ok = await sendApiLogToServer(entry);
//     if (!ok) {
//       pendingApiLogs.push(entry);
//     }
//   }
// }

async function flushPendingApiLogs() {
  if (!accessToken) return;
  if (pendingApiLogs.length === 0) return;

  const pending = [...pendingApiLogs];
  pendingApiLogs = [];

  for (const entry of pending) {
    const ok = await sendApiLogToServer(entry);
    if (!ok) {
      pendingApiLogs.push(entry);
    }
  }
}


// async function recordApiLog(entry) {
//   cachedApiLogs.unshift(entry);
//   cachedApiLogs = cachedApiLogs.slice(0, 200);
//   renderApiLogBox(cachedApiLogs);
//
//   const ok = await sendApiLogToServer(entry);
//   if (!ok) {
//     pendingApiLogs.push(entry);
//     pendingApiLogs = pendingApiLogs.slice(-300);
//   }
// }

  async function recordApiLog(entry) {
    cachedApiLogs.unshift(entry);
    cachedApiLogs = cachedApiLogs.slice(0, 200);
    renderApiLogBox(cachedApiLogs);

    // 임시 중지: API 호출마다 DB에 api_request_log insert 되는 것 방지
    return;

    // const ok = await sendApiLogToServer(entry);
    // if (!ok) {
    //   pendingApiLogs.push(entry);
    //   pendingApiLogs = pendingApiLogs.slice(-300);
    // }
  }

async function apiRequest(method, path, payload, requireAuth = false, suppressAutoLogout = false) {
  // 전역 변수에 토큰이 없으면 localStorage에서 로드 시도
  if (!accessToken) {
    accessToken = localStorage.getItem("interfacehub.jwt") || "";
  }

  if (requireAuth && !accessToken) {
    throw { message: "로그인이 필요합니다. 먼저 로그인 후 다시 시도하세요." };
  }

  const headers = { "Content-Type": "application/json" };
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  const startAt = Date.now();
  let response = null;
  let rawText = "";
  try {
    response = await fetch(`${baseUrl()}${path}`, {
      method,
      headers,
      body: method === "GET" ? undefined : JSON.stringify(payload || {}),
    });
    rawText = await response.text();
    const body = parseBody(rawText);
    if (!response.ok) {
      const errorToThrow = (body && (body.message || body.error || body.code))
        ? body
        : {
          status: response.status,
          statusText: response.statusText,
          path,
          message: body?.message || "",
        };

      if (response.status === 401 && !path.includes("/auth/login") && !suppressAutoLogout) {
        console.error("[AUTH] 401 Unauthorized from:", method, path, "| body:", body);
        sessionExpiredLogout();
      }

      // if (!path.startsWith("/api/v1/debug-logs")) {
      //   await recordApiLog({
      //     occurredAt: nowIso(),
      //     method,
      //     path,
      //     requestBody: truncateText(payload || {}),
      //     responseStatus: response.status,
      //     responseBody: truncateText(body),
      //     errorMessage: getErrorMessage(errorToThrow),
      //     durationMs: Date.now() - startAt,
      //   });
      // }
      // throw errorToThrow;
    }

    if (!path.startsWith("/api/v1/debug-logs")) {
      await recordApiLog({
        occurredAt: nowIso(),
        method,
        path,
        requestBody: truncateText(payload || {}),
        responseStatus: response.status,
        responseBody: truncateText(body),
        errorMessage: "",
        durationMs: Date.now() - startAt,
      });
    }

    return body;
  } catch (error) {
    if (!path.startsWith("/api/v1/debug-logs") && (!response || response.ok)) {
      await recordApiLog({
        occurredAt: nowIso(),
        method,
        path,
        requestBody: truncateText(payload || {}),
        responseStatus: response ? response.status : null,
        responseBody: rawText ? truncateText(parseBody(rawText)) : "",
        errorMessage: getErrorMessage(error),
        durationMs: Date.now() - startAt,
      });
    }
    throw error;
  }
}

function apiGet(path, requireAuth = false) {
  return apiRequest("GET", path, undefined, requireAuth);
}

function apiPost(path, payload, requireAuth = false) {
  return apiRequest("POST", path, payload, requireAuth);
}

function apiPatch(path, payload, requireAuth = false) {
  return apiRequest("PATCH", path, payload, requireAuth);
}

function toIsoLocalDateTime(value) {
  if (!value) return null;
  return value.length === 16 ? `${value}:00` : value;
}

function queryString(params) {
  const q = new URLSearchParams();
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v == null) return;
    const text = String(v).trim();
    if (!text) return;
    q.set(k, text);
  });
  const s = q.toString();
  return s ? `?${s}` : "";
}

function syncDashboardFields() {
  // 대시보드 상단으로 이동하면서 Base URL 입력 필드는 제거됨
}

function showPanel(panelKey) {
  const panelMap = {
    dashboard: "panelDashboard",
    interfaces: "panelInterfaces",
    configs: "panelConfigs",
    dlq: "panelDlq",
    schedules: "panelSchedules",
    audit: "panelAudit",
    settings: "panelSettings",
    executions: "panelExecutions",
    historySearch: "panelHistorySearch",
    retries: "panelRetries",
    output: "panelOutput",
  };
  const navMap = {
    dashboard: "navDashboardBtn",
    interfaces: "navInterfacesBtn",
    configs: "navConfigsBtn",
    dlq: "navDlqBtn",
    schedules: "navSchedulesBtn",
    audit: "navAuditBtn",
    settings: "navSettingsBtn",
    executions: "navExecutionsBtn",
    historySearch: "navHistorySearchBtn",
    retries: "navRetriesBtn",
    output: "navOutputBtn",
  };

  Object.values(panelMap).forEach((id) => $(id)?.classList.add("hidden"));
  Object.values(navMap).forEach((id) => $(id)?.classList.remove("active"));

  const panelId = panelMap[panelKey] || panelMap.interfaces;
  const navId = navMap[panelKey] || navMap.interfaces;
  $(panelId)?.classList.remove("hidden");
  $(navId)?.classList.add("active");
}

function openModal(id) {
  $(id)?.classList.remove("hidden");
}

function closeModal(id) {
  $(id)?.classList.add("hidden");
}

function validateRegisterInputs(username, password, passwordConfirm) {
  const u = (username || "").trim();
  if (u.length < 3 || u.length > 30) return { ok: false, message: "아이디는 3~30자로 입력하세요." };
  if (!/^[a-zA-Z0-9_]+$/.test(u)) return { ok: false, message: "아이디는 영문/숫자/언더스코어(_)만 가능합니다." };

  const p = password || "";
  if (p.length < 8 || p.length > 72) return { ok: false, message: "비밀번호는 8~72자로 입력하세요." };
  if (passwordConfirm != null && p !== (passwordConfirm || "")) return { ok: false, message: "비밀번호 확인이 일치하지 않습니다." };
  if (u && p.toLowerCase().includes(u.toLowerCase())) return { ok: false, message: "비밀번호에 아이디를 포함할 수 없습니다." };
  return { ok: true, message: "" };
}

async function registerUser() {
  try {
    const username = $("regUsername")?.value || "";
    const password = $("regPassword")?.value || "";
    const confirm = $("regPasswordConfirm")?.value || "";

    const validation = validateRegisterInputs(username, password, confirm);
    if (!validation.ok) {
      showToast(validation.message, "error");
      return;
    }

    const data = await apiPost("/api/v1/auth/register", { username: username.trim(), password });
    showOutput(data);
    showToast("회원가입 완료. 로그인 중...", "success");

    closeModal("registerModal");
    setValue("username", username.trim());
    setValue("password", password);

    await login();
  } catch (error) {
    stream.close();
    showOutput(error);

    const code = (error && error.code) || "";
    if (code === "USERNAME_ALREADY_EXISTS") {
      showToast("이미 존재하는 아이디입니다.", "error");
      return;
    }
    if (code === "INVALID_REQUEST") {
      showToast(`회원가입 실패: ${getErrorMessage(error)}`, "error");
      return;
    }
    if (typeof error?.status === "number" && error.status === 403) {
      showToast("회원가입이 비활성화되어 있습니다. (seed 프로필에서 self-signup-enabled=true)", "error");
      return;
    }

    showToast(`회원가입 실패: ${getErrorMessage(error)}`, "error");
  }
}

async function openExecutionDetail(executionId) {
  if (!selectedInterfaceCode || !executionId) {
    return;
  }
  try {
    const detail = await apiGet(
      `/api/v1/interfaces/${encodeURIComponent(selectedInterfaceCode)}/histories/${encodeURIComponent(executionId)}`,
      true
    );

    setText("execDetailExecutionId", detail.executionId || "-");
    setText("execDetailStatus", detail.status || "-");
    setText("execDetailTriggerType", detail.triggerType || "-");
    setText("execDetailProtocolType", detail.protocolType || "-");
    setText("execDetailStartedAt", formatDateTime(detail.startedAt));
    setText("execDetailEndedAt", formatDateTime(detail.endedAt));
    setText("execDetailLatency", detail.latencyMillis ?? "-");
    setText("execDetailErrorCode", detail.errorCode || "-");
    setValue("execDetailErrorMessage", detail.errorMessage || "");
    setValue("execDetailRequestPayload", prettyJson(detail.requestPayload));
    setValue("execDetailResponsePayload", prettyJson(detail.responsePayload));

    const canRetry = detail.status === "FAILED" || detail.status === "TIMEOUT";
    const retryBtn = $("execDetailRetryBtn");
    if (retryBtn) {
      retryBtn.disabled = !canRetry;
      retryBtn.dataset.executionId = detail.executionId || "";
    }

    openModal("executionDetailModal");
  } catch (error) {
    showOutput(error);
    showToast(`상세 조회 실패: ${getErrorMessage(error)}`, "error");
  }
}

function readHistorySearchCriteria() {
  return {
    interfaceCode: $("hsInterfaceCode")?.value?.trim() || null,
    executionIdContains: $("hsExecutionIdContains")?.value?.trim() || null,
    protocolType: $("hsProtocolType")?.value || null,
    triggerType: $("hsTriggerType")?.value || null,
    status: $("hsStatus")?.value || null,
    fromAt: toIsoLocalDateTime($("hsFromAt")?.value || ""),
    toAt: toIsoLocalDateTime($("hsToAt")?.value || ""),
    latencyMin: $("hsLatencyMin")?.value ? Number($("hsLatencyMin").value) : null,
    latencyMax: $("hsLatencyMax")?.value ? Number($("hsLatencyMax").value) : null,
    errorCode: $("hsErrorCode")?.value?.trim() || null,
    errorMessageContains: $("hsErrorMessageContains")?.value?.trim() || null,
  };
}

function showLoginView() {
  $("viewLogin")?.classList.remove("hidden");
  $("viewDashboard")?.classList.add("hidden");
}

function showDashboardView() {
  $("viewLogin")?.classList.add("hidden");
  $("viewDashboard")?.classList.remove("hidden");
  syncDashboardFields();
  showPanel("dashboard");
}

function renderBasicInfo(data) {
  setText("bInterfaceCode", data.interfaceCode || "-");
  setText("bInterfaceName", data.name || "-");
  setText("bProtocol", data.protocolType || "-");
  
  const bStatus = $("bStatus");
  if (bStatus) {
    bStatus.innerHTML = "";
    bStatus.appendChild(createStatusPill(data.status));
  }

  setText("bOwnerTeam", data.ownerTeam || "-");
  setText("bBusiness", data.businessCategory || "-");
  setText("bExternalOrg", data.externalOrg || "-");
  setText("bSla", data.slaMillis ?? "-");
  setText("bLastExecutedAt", formatDateTime(data.lastExecutedAt));

  // Task 3: Populate dummy data
  const samples = {
    REST: { policyNo: "P202604250001", customerId: "C1001", amount: 50000, currency: "KRW" },
    SOAP: { "soapenv:Envelope": { "soapenv:Body": { "query": { policyNo: "P202604250001" } } } },
    MQ: { eventId: "evt-999", type: "CLAIM_REQUEST", data: { claimId: "CLM-001" } },
    BATCH: { jobName: data.interfaceCode, params: { targetDate: "2026-04-25" } },
    SFTP: { filename: "report_20260425.csv", mode: "UPLOAD" }
  };
  const sample = samples[data.protocolType] || { info: "No sample for " + data.protocolType };
  setValue("execPayload", JSON.stringify(sample, null, 2));
}

function renderConfigs(pageData) {
  const table = $("configsTable")?.querySelector("tbody");
  if (!table) return;
  table.innerHTML = "";

  (pageData.content || []).forEach((config) => {
    const tr = document.createElement("tr");
    [
      config.configId,
      config.version,
      config.environment,
      config.endpoint,
      config.timeoutMillis,
      config.published ? "Y" : "N",
      formatDateTime(config.createdAt),
    ].forEach((value) => {
      const td = document.createElement("td");
      td.textContent = value ?? "-";
      tr.appendChild(td);
    });
    table.appendChild(tr);
  });
}

function createStatusPill(status) {
  const span = document.createElement("span");
  span.className = `status-pill ${statusClass(status)}`;
  span.textContent = statusText(status);
  return span;
}

function renderFailureDetail(history) {
  if (!history) {
    setText("selectedExecutionId", "-");
    setText("failCategoryText", "-");
    setText("failReasonText", "-");
    $("failRequestPayload").value = "";
    $("failResponsePayload").value = "";
    $("failErrorPayload").value = "";
    return;
  }

  const category = classifyErrorCategory(history.errorCode, history.errorMessage);
  setText("selectedExecutionId", history.executionId);
  setText("failCategoryText", categoryText(category));
  setText("failReasonText", businessReason(history.errorCode, category));
  $("failRequestPayload").value = prettyJson(history.requestPayload);
  $("failResponsePayload").value = prettyJson(history.responsePayload);
  $("failErrorPayload").value = history.errorMessage || history.errorCode || "";
}

function selectExecution(executionId) {
  selectedExecutionId = executionId;
  renderHistories();
  renderFailureDetail(getCurrentHistory());
  renderFlow();
}

function renderHistories() {
  const tbody = $("historyTable")?.querySelector("tbody");
  if (!tbody) return;

  const items = getFilteredHistories();
  tbody.innerHTML = "";

  items.forEach((history) => {
    const tr = document.createElement("tr");
    const failed = history.status === "FAILED" || history.status === "TIMEOUT" || history.status === "CANCELLED";
    if (failed) {
      tr.classList.add("failure-row");
    }
    if (history.executionId === selectedExecutionId) {
      tr.classList.add("selected-row");
    }

    const checkTd = document.createElement("td");
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.className = "history-select";
    checkbox.value = history.executionId;
    checkbox.disabled = !failed;
    checkbox.addEventListener("change", syncSelectAllState);
    checkTd.appendChild(checkbox);
    tr.appendChild(checkTd);

    const statusTd = document.createElement("td");
    statusTd.appendChild(createStatusPill(history.status));
    tr.appendChild(statusTd);

    const titleTd = document.createElement("td");
    const titleStrong = document.createElement("strong");
    titleStrong.textContent = `Alert ${history.executionId}`;
    const titleSub = document.createElement("div");
    titleSub.textContent = history.errorMessage || "실패/지연 알림 이벤트";
    titleTd.appendChild(titleStrong);
    titleTd.appendChild(titleSub);
    tr.appendChild(titleTd);

    const ackTd = document.createElement("td");
    const matchedTask = cachedRetryTasks.find((task) => task.originalExecutionId === history.executionId);
    const ackChip = document.createElement("span");
    ackChip.className = "ack-chip";
    ackChip.textContent = matchedTask ? matchedTask.status : "ACK";
    ackTd.appendChild(ackChip);
    tr.appendChild(ackTd);

    const activatedTd = document.createElement("td");
    const timeText = formatDateTime(history.startedAt);
    activatedTd.textContent = timeText.includes(" ") ? timeText.split(" ")[1] : timeText;
    tr.appendChild(activatedTd);

    const conditionTd = document.createElement("td");
    const conditionWrap = document.createElement("div");
    conditionWrap.className = "cond-wrap";
    const conditionBar = document.createElement("div");
    conditionBar.className = "cond-bar";
    const latency = Math.max(Number(history.latencyMillis || 0), 1);
    const ratio = Math.min(Math.round((latency / 3000) * 100), 100);
    conditionBar.style.width = `${ratio}%`;
    conditionWrap.appendChild(conditionBar);
    conditionTd.appendChild(conditionWrap);
    tr.appendChild(conditionTd);

    const tagTd = document.createElement("td");
    tagTd.textContent = history.errorCode || `#${history.executionId.slice(-6)}`;
    tr.appendChild(tagTd);

    const actionTd = document.createElement("td");
    const detailBtn = document.createElement("button");
    detailBtn.className = "secondary";
    detailBtn.textContent = "상세";
    detailBtn.addEventListener("click", async () => {
      selectExecution(history.executionId);
      await openExecutionDetail(history.executionId);
    });
    actionTd.appendChild(detailBtn);

    if (failed) {
      const retryBtn = document.createElement("button");
      retryBtn.textContent = "Retry";
      retryBtn.addEventListener("click", async () => {
        selectExecution(history.executionId);
        await singleRetry();
      });
      actionTd.appendChild(retryBtn);
    }
    tr.appendChild(actionTd);

    tr.addEventListener("click", (event) => {
      if (event.target instanceof HTMLButtonElement || event.target instanceof HTMLInputElement) {
        return;
      }
      selectExecution(history.executionId);
    });

    tbody.appendChild(tr);
  });

  if (!selectedExecutionId && items.length > 0) {
    selectedExecutionId = items[0].executionId;
  }

  syncSelectAllState();
  renderFailureDetail(getCurrentHistory());
}

function renderRetryTasks() {
  const tbody = $("retryTable")?.querySelector("tbody");
  if (!tbody) return;
  tbody.innerHTML = "";

  cachedRetryTasks
    .filter((task) => !selectedInterfaceCode || task.interfaceCode === selectedInterfaceCode)
    .forEach((task) => {
      const tr = document.createElement("tr");
      [
        task.id,
        task.originalExecutionId,
        task.status,
        task.requester,
        task.approver || "-",
        formatDateTime(task.createdAt),
      ].forEach((value) => {
        const td = document.createElement("td");
        td.textContent = value ?? "-";
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
  renderKanbanBoard();
}

function renderKanbanBoard() {
  const tasks = cachedRetryTasks.filter(
    (t) => !selectedInterfaceCode || t.interfaceCode === selectedInterfaceCode
  );

  const cols = {
    pending:  tasks.filter((t) => t.status === "PENDING"),
    approved: tasks.filter((t) => t.status === "APPROVED" || t.status === "EXECUTED"),
    failed:   tasks.filter((t) => t.status === "FAILED"   || t.status === "REJECTED"),
  };

  setText("kanbanCountPending",  String(cols.pending.length));
  setText("kanbanCountApproved", String(cols.approved.length));
  setText("kanbanCountFailed",   String(cols.failed.length));

  function fillCol(colId, items) {
    const el = $(colId);
    if (!el) return;
    el.innerHTML = "";
    if (!items.length) {
      const empty = document.createElement("div");
      empty.className = "kanban-empty";
      empty.textContent = "없음";
      el.appendChild(empty);
      return;
    }
    items.forEach((t) => {
      const card = document.createElement("div");
      card.className = "kanban-card";
      const pill = createStatusPill(t.status);
      const execId = document.createElement("div");
      execId.className = "exec-id";
      execId.textContent = t.originalExecutionId || "-";
      const meta = document.createElement("div");
      meta.textContent = `${t.requester || "-"} · ${formatDateTime(t.createdAt)}`;
      card.appendChild(pill);
      card.appendChild(execId);
      card.appendChild(meta);
      el.appendChild(card);
    });
  }

  fillCol("kanbanColPending",  cols.pending);
  fillCol("kanbanColApproved", cols.approved);
  fillCol("kanbanColFailed",   cols.failed);
}

function bindRetriesSubtabs() {
  const buttons = Array.from(document.querySelectorAll("#retriesSubtabs .subtab"));
  if (buttons.length === 0) return;
  buttons.forEach((btn) => {
    btn.addEventListener("click", () => {
      buttons.forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      const tab = btn.dataset.rtab;
      $("rtabAlerts")?.classList.toggle("hidden", tab !== "alerts");
      $("rtabKanban")?.classList.toggle("hidden", tab !== "kanban");
      if (tab === "kanban") renderKanbanBoard();
    });
  });
}

function percentText(value) {
  if (value == null || Number.isNaN(value)) {
    return "-";
  }
  return `${(Number(value) * 100).toFixed(1)}%`;
}

function buildHistoryCharts(rows) {
  if (hsTimelineChart) hsTimelineChart.destroy();
  if (hsStatusDonutChart) hsStatusDonutChart.destroy();
  hsTimelineChart = null;
  hsStatusDonutChart = null;

  const area = $("hsChartArea");
  if (!area) return;

  if (!rows || rows.length === 0) {
    area.classList.add("hidden");
    return;
  }
  area.classList.remove("hidden");

  // 시간별 집계
  const byHour = {};
  rows.forEach((r) => {
    if (!r.startedAt) return;
    const hour = r.startedAt.slice(0, 13).replace("T", " ");
    byHour[hour] = (byHour[hour] || 0) + 1;
  });

  // 상태별 집계
  const byStatus = {};
  rows.forEach((r) => {
    const s = r.status || "UNKNOWN";
    byStatus[s] = (byStatus[s] || 0) + 1;
  });

  const sortedHours = Object.keys(byHour).sort();
  const timelineData = sortedHours.map((h) => byHour[h]);

  const statusLabels = Object.keys(byStatus);
  const statusData = statusLabels.map((s) => byStatus[s]);
  const statusColors = statusLabels.map((s) => STATUS_COLORS[s] || "#94a3b8");

  const timelineCtx = $("hsTimelineChart")?.getContext("2d");
  if (timelineCtx) {
    hsTimelineChart = new Chart(timelineCtx, {
      type: "line",
      data: {
        labels: sortedHours.map((h) => h.split(" ")[1] + "시"),
        datasets: [
          {
            label: "실행 수",
            data: timelineData,
            borderColor: "#8b5cf6",
            backgroundColor: "rgba(139, 92, 246, 0.1)",
            fill: true,
            tension: 0.3,
          },
        ],
      },
      options: {
        responsive: true,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } },
      },
    });
  }

  const statusCtx = $("hsStatusDonut")?.getContext("2d");
  if (statusCtx) {
    hsStatusDonutChart = new Chart(statusCtx, {
      type: "doughnut",
      data: {
        labels: statusLabels,
        datasets: [
          {
            data: statusData,
            backgroundColor: statusColors,
            borderWidth: 0,
          },
        ],
      },
      options: {
        responsive: true,
        plugins: { legend: { position: "right" } },
        cutout: "60%",
      },
    });
  }
}

function renderHistorySearchResults(pageData) {
  cachedHistorySearch = {
    page: pageData.page ?? 0,
    size: pageData.size ?? 50,
    totalPages: pageData.totalPages ?? 0,
    totalElements: pageData.totalElements ?? 0,
    criteria: cachedHistorySearch.criteria,
    content: pageData.content || [],
  };

  setText(
    "hsResultMeta",
    `page=${cachedHistorySearch.page + 1}/${Math.max(cachedHistorySearch.totalPages, 1)}  total=${cachedHistorySearch.totalElements}`
  );

  buildHistoryCharts(cachedHistorySearch.content);

  const tbody = $("hsResultTable")?.querySelector("tbody");
  if (!tbody) return;
  tbody.innerHTML = "";

  (cachedHistorySearch.content || []).forEach((row) => {
    const tr = document.createElement("tr");
    const cols = [
      row.interfaceCode || "-",
      row.executionId || "-",
      row.status || "-",
      row.triggerType || "-",
      formatDateTime(row.startedAt),
      row.latencyMillis ?? 0,
      row.errorCode || "-",
    ];
    cols.forEach((v) => {
      const td = document.createElement("td");
      td.textContent = String(v);
      tr.appendChild(td);
    });

    const actionTd = document.createElement("td");
    const detailBtn = document.createElement("button");
    detailBtn.className = "secondary";
    detailBtn.textContent = "상세";
    detailBtn.addEventListener("click", async () => {
      if (!row.interfaceCode || !row.executionId) return;
      selectedInterfaceCode = row.interfaceCode;
      if ($("interfaceSelect")) {
        $("interfaceSelect").value = selectedInterfaceCode;
      }
      await openExecutionDetail(row.executionId);
    });
    actionTd.appendChild(detailBtn);

    const jumpBtn = document.createElement("button");
    jumpBtn.textContent = "이력으로";
    jumpBtn.addEventListener("click", async () => {
      if (!row.interfaceCode) return;
      selectedInterfaceCode = row.interfaceCode;
      const select = $("interfaceSelect");
      if (select) {
        select.value = selectedInterfaceCode;
      }
      await loadInterfaceDetail();
      showPanel("executions");
      await refreshRuntimeData(true);
    });
    actionTd.appendChild(jumpBtn);

    tr.appendChild(actionTd);
    tbody.appendChild(tr);
  });

  const prevBtn = $("hsPrevBtn");
  const nextBtn = $("hsNextBtn");
  if (prevBtn) prevBtn.disabled = cachedHistorySearch.page <= 0;
  if (nextBtn) nextBtn.disabled = cachedHistorySearch.page >= Math.max(cachedHistorySearch.totalPages - 1, 0);
}

async function searchHistories(page = 0) {
  const size = Number($("hsPageSize")?.value || "50");
  const criteria = cachedHistorySearch.criteria || readHistorySearchCriteria();
  cachedHistorySearch.criteria = criteria;

  const params = {
    ...criteria,
    page,
    size,
  };
  const url = `/api/v1/histories/search${queryString(params)}`;
  const data = await apiGet(url, true);
  renderHistorySearchResults(data);
  showOutput(data);
  return data;
}

function triggerHistorySearch() {
  cachedHistorySearch.criteria = readHistorySearchCriteria();
  return searchHistories(0);
}

function readAuditCriteria() {
  return {
    actor: $("auditActor")?.value?.trim() || null,
    action: $("auditAction")?.value?.trim() || null,
    targetType: $("auditTargetType")?.value?.trim() || null,
    fromDate: $("auditFrom")?.value || null,
    toDate: $("auditTo")?.value || null,
  };
}

function safePrettyJsonText(text) {
  const raw = (text || "").trim();
  if (!raw) return "{}";
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch (_e) {
    return raw;
  }
}

function renderAuditDiff(beforeValue, afterValue) {
  const beforeText = safePrettyJsonText(beforeValue);
  const afterText = safePrettyJsonText(afterValue);
  const bLines = beforeText.split("\n");
  const aLines = afterText.split("\n");
  const max = Math.max(bLines.length, aLines.length);

  const beforeBox = $("auditBeforeBox");
  const afterBox = $("auditAfterBox");
  if (!beforeBox || !afterBox) return;
  beforeBox.innerHTML = "";
  afterBox.innerHTML = "";

  for (let i = 0; i < max; i += 1) {
    const b = bLines[i] ?? "";
    const a = aLines[i] ?? "";
    const same = b === a;

    const bDiv = document.createElement("div");
    bDiv.className = "diff-line";
    bDiv.textContent = b;
    if (!same && b) bDiv.classList.add("del");
    beforeBox.appendChild(bDiv);

    const aDiv = document.createElement("div");
    aDiv.className = "diff-line";
    aDiv.textContent = a;
    if (!same && a) aDiv.classList.add("add");
    afterBox.appendChild(aDiv);
  }
}

function openAuditDetail(row) {
  if (!row) return;
  const meta = $("auditDetailMeta");
  if (meta) {
    meta.textContent = `ID=${row.id} / ${row.actor || "-"} / ${row.action || "-"} / ${row.targetType || "-"}:${row.targetId || "-"}`;
  }
  renderAuditDiff(row.beforeValue, row.afterValue);
  openModal("auditDetailModal");
}

function renderAuditTable(pageData) {
  const tbody = $("auditTable")?.querySelector("tbody");
  if (!tbody) return;
  tbody.innerHTML = "";

  cachedAuditSearch = {
    content: pageData?.content || [],
    page: pageData?.page ?? 0,
    size: pageData?.size ?? 20,
    totalElements: pageData?.totalElements ?? 0,
    totalPages: pageData?.totalPages ?? 0,
    criteria: cachedAuditSearch.criteria,
  };

  const meta = $("auditMeta");
  if (meta) {
    meta.textContent = `page=${cachedAuditSearch.page + 1}/${Math.max(cachedAuditSearch.totalPages, 1)}  total=${cachedAuditSearch.totalElements}`;
  }

  (cachedAuditSearch.content || []).forEach((row) => {
    const tr = document.createElement("tr");
    const cols = [
      row.id,
      row.actor,
      row.action,
      row.targetType,
      row.targetId,
      formatDateTime(row.createdAt),
    ];
    cols.forEach((v) => {
      const td = document.createElement("td");
      td.textContent = v == null ? "-" : String(v);
      tr.appendChild(td);
    });

    const tdDetail = document.createElement("td");
    const btn = document.createElement("button");
    btn.className = "secondary";
    btn.textContent = "상세";
    btn.addEventListener("click", () => openAuditDetail(row));
    tdDetail.appendChild(btn);
    tr.appendChild(tdDetail);

    tbody.appendChild(tr);
  });

  const prev = $("auditPrevBtn");
  const next = $("auditNextBtn");
  if (prev) prev.disabled = cachedAuditSearch.page <= 0;
  if (next) next.disabled = cachedAuditSearch.page >= Math.max(cachedAuditSearch.totalPages - 1, 0);
}

async function searchAuditLogs(page = 0) {
  const size = cachedAuditSearch.size || 20;
  const criteria = cachedAuditSearch.criteria || readAuditCriteria();
  cachedAuditSearch.criteria = criteria;
  const params = { ...criteria, page, size };
  const data = await apiGet(`/api/v1/audit-logs${queryString(params)}`, true);
  renderAuditTable(data);
  showOutput(data);
  return data;
}

function triggerAuditSearch() {
  cachedAuditSearch.criteria = readAuditCriteria();
  return searchAuditLogs(0);
}

function renderDashboardSummary(summary) {
  if (!summary) {
    return;
  }

  setText("dashTotalInterfaces", summary.totalInterfaces ?? "-");
  setText("dashActiveInterfaces", summary.activeInterfaces ?? "-");
  setText("dashInactiveInterfaces", summary.inactiveInterfaces ?? "-");

  setText("dashExecTotal", summary.executions?.total ?? "-");
  setText("dashExecSuccess", summary.executions?.success ?? "-");
  setText("dashExecFail", (summary.executions?.failed ?? 0) + (summary.executions?.cancelled ?? 0));
  setText("dashExecTimeout", summary.executions?.timeout ?? "-");
  setText("dashExecSuccessRate", percentText(summary.executions?.successRate));
  setText("dashSlaBreaches", summary.slaBreaches ?? "-");
  setText("dashDlqRecent", summary.dlqRecent ?? "-");

  const retry = summary.retryTasks || {};
  setText("dashRetryPendingKpi", retry.PENDING ?? 0);
  setNavBadge("badgeRetryPending", retry.PENDING ?? 0);

  const dlqReplay = summary.dlqReplayRequests || {};
  setText("dashDlqReplayPendingKpi", dlqReplay.PENDING ?? 0);
  setNavBadge("badgeDlqPending", dlqReplay.PENDING ?? 0);

  enhanceDashboardKpis(summary, retry, dlqReplay);
  renderDashboardCharts(summary);
  lastDashboardSummarySnapshot = summary;

  const tbody = $("dashTopFailuresTable")?.querySelector("tbody");
  if (!tbody) {
    return;
  }
  tbody.innerHTML = "";

  (summary.topFailures || []).forEach((row) => {
    const tr = document.createElement("tr");
    const cols = [
      row.interfaceCode,
      row.total ?? 0,
      row.failures ?? 0,
      row.failed ?? 0,
      row.timeout ?? 0,
      row.cancelled ?? 0,
      percentText(row.failureRate),
    ];
    cols.forEach((value) => {
      const td = document.createElement("td");
      td.textContent = value == null ? "-" : String(value);
      tr.appendChild(td);
    });

    const actionTd = document.createElement("td");
    const btn = document.createElement("button");
    btn.className = "secondary";
    btn.textContent = "보기";
    btn.addEventListener("click", async () => {
      if (!row.interfaceCode) return;
      selectedInterfaceCode = row.interfaceCode;
      const select = $("interfaceSelect");
      if (select) {
        select.value = selectedInterfaceCode;
      }
      await loadInterfaceDetail();
      showPanel("executions");
      await refreshRuntimeData(true);
    });
    actionTd.appendChild(btn);
    tr.appendChild(actionTd);

    tbody.appendChild(tr);
  });
}

function enhanceDashboardKpis(summary, retry, dlqReplay) {
  const prev = lastDashboardSummarySnapshot;

  setKpiBrand("dashTotalInterfaces", "#8b5cf6");
  setKpiBrand("dashExecTotal", "#3b82f6");
  setKpiBrand("dashSlaBreaches", "#f59e0b");
  setKpiBrand("dashRetryPendingKpi", "#f59e0b");

  const successRate = Number(summary?.executions?.successRate ?? 0);
  const successColor = successRate >= 0.98 ? "#22c55e" : successRate >= 0.95 ? "#f59e0b" : "#ef4444";
  setKpiBrand("dashExecSuccessRate", successColor);

  const dlqPending = Number((dlqReplay || {}).PENDING ?? 0);
  setKpiBrand("dashDlqReplayPendingKpi", dlqPending > 0 ? "#ef4444" : "#22c55e");

  setTrendNumber("dashTrendTotalInterfaces", summary?.totalInterfaces, prev?.totalInterfaces);
  setTrendNumber("dashTrendExecTotal", summary?.executions?.total, prev?.executions?.total);
  setTrendPercentPoint("dashTrendSuccessRate", summary?.executions?.successRate, prev?.executions?.successRate);
  setTrendNumber("dashTrendSlaBreaches", summary?.slaBreaches, prev?.slaBreaches, true);
  setTrendNumber("dashTrendRetryPending", (retry || {}).PENDING, (prev?.retryTasks || {}).PENDING, true);
  setTrendNumber("dashTrendDlqPending", (dlqReplay || {}).PENDING, (prev?.dlqReplayRequests || {}).PENDING, true);
}

function setKpiBrand(strongId, color) {
  const strong = $(strongId);
  const card = strong?.closest?.(".kpi-card");
  if (!card) return;
  card.style.setProperty("--brand", color);
}

function setTrendNumber(elementId, current, previous, invert = false) {
  const el = $(elementId);
  if (!el) return;

  if (previous == null || Number.isNaN(Number(previous)) || current == null || Number.isNaN(Number(current))) {
    el.textContent = "-";
    el.classList.remove("up", "down");
    return;
  }

  const delta = Number(current) - Number(previous);
  const effective = invert ? -delta : delta;

  if (effective > 0) {
    el.textContent = `▲ +${Math.abs(delta)}`;
    el.classList.add("up");
    el.classList.remove("down");
    return;
  }
  if (effective < 0) {
    el.textContent = `▼ -${Math.abs(delta)}`;
    el.classList.add("down");
    el.classList.remove("up");
    return;
  }

  el.textContent = "— 0";
  el.classList.remove("up", "down");
}

function setTrendPercentPoint(elementId, current, previous) {
  const el = $(elementId);
  if (!el) return;

  if (previous == null || current == null) {
    el.textContent = "-";
    el.classList.remove("up", "down");
    return;
  }

  const deltaPp = (Number(current) - Number(previous)) * 100.0;
  if (Number.isNaN(deltaPp)) {
    el.textContent = "-";
    el.classList.remove("up", "down");
    return;
  }

  const abs = Math.abs(deltaPp).toFixed(1);
  if (deltaPp > 0) {
    el.textContent = `▲ +${abs}%p`;
    el.classList.add("up");
    el.classList.remove("down");
    return;
  }
  if (deltaPp < 0) {
    el.textContent = `▼ -${abs}%p`;
    el.classList.add("down");
    el.classList.remove("up");
    return;
  }

  el.textContent = "— 0.0%p";
  el.classList.remove("up", "down");
}

async function loadDashboardSummary(windowHours = 24) {
  try {
    const summary = await apiGet(`/api/v1/dashboard/summary?windowHours=${encodeURIComponent(windowHours)}`, true);
    renderDashboardSummary(summary);
    return summary;
  } catch (error) {
    showOutput(error);
    showToast(`대시보드 조회 실패: ${getErrorMessage(error)}`, "error");
    return null;
  }
}

async function loadDashboardInterfaceStats(windowHours = 24) {
  try {
    const stats = await apiGet(`/api/v1/dashboard/interfaces?windowHours=${encodeURIComponent(windowHours)}`, true);
    renderHealthGrid(stats);
    return stats;
  } catch (error) {
    showOutput(error);
    showToast(`인터페이스 헬스 조회 실패: ${getErrorMessage(error)}`, "error");
    return null;
  }
}

async function loadDashboardSlaBreaches(windowHours = 24) {
  try {
    const rows = await apiGet(`/api/v1/dashboard/sla-breaches?windowHours=${encodeURIComponent(windowHours)}`, true);
    renderDashboardSlaBar(rows || []);
    return rows;
  } catch (error) {
    showOutput(error);
    showToast(`SLA breaches load failed: ${getErrorMessage(error)}`, "error");
    renderDashboardSlaBar([]);
    return null;
  }
}

function renderDashboardSlaBar(rows) {
  if (!window.Chart) return;
  const canvas = $("dashSlaBar");
  if (!canvas) return;

  try {
    if (dashSlaBarChart) dashSlaBarChart.destroy();
  } catch (_e) {}
  dashSlaBarChart = null;

  const list = Array.isArray(rows) ? rows.slice(0, 10) : [];
  const labels = list.map((r) => r.interfaceCode);
  const values = list.map((r) => r.slaBreachCount ?? 0);

  dashSlaBarChart = new Chart(canvas.getContext("2d"), {
    type: "bar",
    data: {
      labels,
      datasets: [{
        label: "breaches",
        data: values,
        backgroundColor: "rgba(245, 158, 11, 0.75)",
        borderRadius: 8,
      }],
    },
    options: {
      responsive: true,
      indexAxis: "y",
      plugins: { legend: { display: false } },
      scales: { x: { beginAtZero: true } },
    },
  });
}

// async function loadDashboardActivityFeed() {
//   try {
//     const data = await apiGet(`/api/v1/histories/search?page=0&size=10`, true);
//     renderDashboardActivityFeed(data?.content || []);
//     return data;
//   } catch (error) {
//     showOutput(error);
//     showToast(`Activity feed load failed: ${getErrorMessage(error)}`, "error");
//     renderDashboardActivityFeed([]);
//     return null;
//   }
// }

async function loadDashboardActivityFeed() {
  // 임시 중지: execution_history 자동 조회 방지
  return null;
}

function renderDashboardActivityFeed(items) {
  const list = $("dashActivityFeed");
  if (!list) return;
  list.innerHTML = "";

  const rows = Array.isArray(items) ? items : [];
  rows.forEach((row) => {
    const li = document.createElement("li");
    li.className = "feed-item";

    const status = String(row?.status || "");
    const dot = document.createElement("span");
    dot.className = "feed-dot " + (status === "SUCCESS" ? "success" : status === "TIMEOUT" ? "timeout" : "fail");

    const label = document.createElement("span");
    label.textContent = `[${status || "-"}]`;

    const code = document.createElement("span");
    code.className = "feed-code";
    code.textContent = row?.interfaceCode || "-";

    const time = document.createElement("span");
    time.className = "feed-time";
    time.textContent = timeAgoText(row?.startedAt);

    const latency = document.createElement("span");
    latency.className = "feed-latency";
    if (status === "SUCCESS") {
      latency.textContent = `${row?.latencyMillis ?? 0}ms`;
    } else if (status === "TIMEOUT") {
      latency.textContent = "타임아웃";
    } else {
      latency.textContent = row?.errorCode || "-";
    }

    li.appendChild(dot);
    li.appendChild(label);
    li.appendChild(code);
    li.appendChild(time);
    li.appendChild(latency);
    list.appendChild(li);
  });
}

function timeAgoText(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "-";
  }
  const diffMs = Date.now() - date.getTime();
  const sec = Math.floor(diffMs / 1000);
  if (sec < 60) return `${Math.max(sec, 0)}초 전`;
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}분 전`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}시간 전`;
  const day = Math.floor(hour / 24);
  return `${day}일 전`;
}

async function loadIncidentSummary(windowHours = 24) {
  const box = $("dashIncidentText");
  const meta = $("dashIncidentMeta");
  if (box) box.textContent = "요약 생성 중...";
  if (meta) meta.textContent = "-";
  try {
    const data = await apiGet(`/api/v1/incidents/summary?hoursBack=${encodeURIComponent(windowHours)}&limit=100`, true);
    if (box) box.textContent = data.summary || "-";
    if (meta) {
      meta.textContent = `분석 ${data.analyzedCount ?? 0}건 / 기간 ${data.hoursBack ?? windowHours}h / 생성 ${formatDateTime(data.generatedAt)}`;
    }
    return data;
  } catch (error) {
    if (box) box.textContent = `요약 조회 실패: ${getErrorMessage(error)}`;
    showOutput(error);
    return null;
  }
}

async function reloadDashboard(windowHours = dashboardWindowHours) {
  dashboardWindowHours = windowHours;
  await Promise.all([
    loadDashboardSummary(dashboardWindowHours),
    loadDashboardInterfaceStats(dashboardWindowHours),
    loadDashboardSlaBreaches(dashboardWindowHours),
    //loadDashboardActivityFeed(),
  ]);
}

function destroyDashboardCharts() {
  try {
    if (dashExecDonutChart) dashExecDonutChart.destroy();
  } catch (_e) {}
  try {
    if (dashTopFailBarChart) dashTopFailBarChart.destroy();
  } catch (_e) {}
  try {
    if (dashLatencyTrendChart) dashLatencyTrendChart.destroy();
  } catch (_e) {}
  try {
    if (dashRetryDonutChart) dashRetryDonutChart.destroy();
  } catch (_e) {}
  try {
    if (dashDlqReplayDonutChart) dashDlqReplayDonutChart.destroy();
  } catch (_e) {}
  dashExecDonutChart = null;
  dashTopFailBarChart = null;
  dashLatencyTrendChart = null;
  dashRetryDonutChart = null;
  dashDlqReplayDonutChart = null;
}

function makeDonutChart(canvasId, legendId, config) {
  const canvas = $(canvasId);
  if (!canvas || !window.Chart) {
    return null;
  }
  const labels = config.labels || [];
  const data = config.data || [];
  const colors = config.colors || [];

  const chart = new Chart(canvas.getContext("2d"), {
    type: "doughnut",
    data: {
      labels,
      datasets: [{
        data,
        backgroundColor: colors,
        borderWidth: 0,
      }],
    },
    options: {
      responsive: true,
      plugins: { legend: { display: false } },
      cutout: "70%",
    },
  });

  renderDonutLegend(legendId, labels, data, colors);
  return chart;
}

function renderDonutLegend(legendId, labels, data, colors) {
  const legend = $(legendId);
  if (!legend) return;
  legend.innerHTML = "";

  labels.forEach((label, index) => {
    const item = document.createElement("div");
    item.className = "donut-legend-item";

    const dot = document.createElement("span");
    dot.className = "donut-legend-dot";
    dot.style.background = colors[index] || "#94a3b8";

    const text = document.createElement("span");
    text.textContent = `${label} ${data[index] ?? 0}`;

    item.appendChild(dot);
    item.appendChild(text);
    legend.appendChild(item);
  });
}

function renderDashboardCharts(summary) {
  if (!summary) return;
  if (!window.Chart) return;
  destroyDashboardCharts();

  const donutEl = $("dashExecDonut");
  if (donutEl) {
    const labels = ["SUCCESS", "FAILED", "TIMEOUT", "CANCELLED"];
    const data = [
      summary.executions?.success ?? 0,
      summary.executions?.failed ?? 0,
      summary.executions?.timeout ?? 0,
      summary.executions?.cancelled ?? 0,
    ];
    dashExecDonutChart = new Chart(donutEl.getContext("2d"), {
      type: "doughnut",
      data: {
        labels,
        datasets: [{
          data,
          backgroundColor: ["#24a36b", "#d35151", "#d79a2c", "#8998b0"],
          borderWidth: 0,
        }],
      },
      options: {
        responsive: true,
        plugins: { legend: { position: "bottom" } },
        cutout: "64%",
      },
    });
  }

  const retry = summary.retryTasks || {};
  dashRetryDonutChart = makeDonutChart("dashRetryDonut", "dashRetryLegend", {
    labels: ["PENDING", "APPROVED", "EXECUTED", "FAILED", "REJECTED"],
    data: [
      retry.PENDING ?? 0,
      retry.APPROVED ?? 0,
      retry.EXECUTED ?? 0,
      retry.FAILED ?? 0,
      retry.REJECTED ?? 0,
    ],
    colors: ["#f59e0b", "#3b82f6", "#22c55e", "#ef4444", "#94a3b8"],
  });

  const dlqReplay = summary.dlqReplayRequests || {};
  dashDlqReplayDonutChart = makeDonutChart("dashDlqReplayDonut", "dashDlqReplayLegend", {
    labels: ["PENDING", "APPROVED", "EXECUTED", "FAILED", "REJECTED"],
    data: [
      dlqReplay.PENDING ?? 0,
      dlqReplay.APPROVED ?? 0,
      dlqReplay.EXECUTED ?? 0,
      dlqReplay.FAILED ?? 0,
      dlqReplay.REJECTED ?? 0,
    ],
    colors: ["#ef4444", "#3b82f6", "#22c55e", "#f59e0b", "#94a3b8"],
  });

  // Task 2: Latency Trend Chart
  const latencyEl = $("dashLatencyTrend");
  if (latencyEl) {
    const labels = ["00h", "04h", "08h", "12h", "16h", "20h"];
    const data = [320, 450, 1200, 800, 1500, 600]; // Mock data
    dashLatencyTrendChart = new Chart(latencyEl.getContext("2d"), {
      type: "line",
      data: {
        labels,
        datasets: [{
          label: "Avg Latency (ms)",
          data,
          borderColor: "#8b5cf6",
          backgroundColor: "rgba(139, 92, 246, 0.1)",
          fill: true,
          tension: 0.4,
        }],
      },
      options: {
        responsive: true,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true } },
      },
    });
  }

  const barEl = $("dashTopFailBar");
  const rows = summary.topFailures || [];
  if (barEl) {
    const labels = rows.map((r) => r.interfaceCode);
    const values = rows.map((r) => r.failures ?? 0);
    dashTopFailBarChart = new Chart(barEl.getContext("2d"), {
      type: "bar",
      data: {
        labels,
        datasets: [{
          label: "failures",
          data: values,
          backgroundColor: "rgba(211, 81, 81, 0.75)",
          borderRadius: 8,
        }],
      },
      options: {
        responsive: true,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true } },
        onClick: async (_evt, elements) => {
          if (!elements || elements.length === 0) return;
          const index = elements[0].index;
          const code = labels[index];
          if (!code) return;
          try {
            selectedInterfaceCode = code;
            const select = $("interfaceSelect");
            if (select) select.value = selectedInterfaceCode;
            await loadInterfaceDetail();
            showPanel("executions");
            await refreshRuntimeData(true);
          } catch (e) {
            showOutput(e);
          }
        },
      },
    });
  }
}

function renderHealthGrid(rows) {
  const grid = $("dashHealthGrid");
  if (!grid) return;
  grid.innerHTML = "";

  (rows || []).slice(0, 24).forEach((row) => {
    const card = document.createElement("div");
    card.className = "health-card";

    const code = document.createElement("div");
    code.className = "health-code";
    code.textContent = row.interfaceCode || "-";

    const rateWrap = document.createElement("div");
    rateWrap.className = "rate-bar-wrap";
    const fill = document.createElement("div");
    fill.className = "rate-bar-fill";
    const rate = Math.max(0, Math.min(100, Math.round((row.successRate ?? 0) * 100)));
    fill.style.width = `${rate}%`;
    if (rate < 80) fill.classList.add("fail");
    else if (rate < 95) fill.classList.add("warn");
    rateWrap.appendChild(fill);

    const row1 = document.createElement("div");
    row1.className = "health-row";
    row1.innerHTML = `<span>성공률</span><strong>${rate}%</strong>`;

    const row2 = document.createElement("div");
    row2.className = "health-row";
    const avg = row.avgLatencyMs == null ? "-" : `${Math.round(row.avgLatencyMs)}ms`;
    row2.innerHTML = `<span>평균 레이턴시</span><strong>${avg}</strong>`;

    const row3 = document.createElement("div");
    row3.className = "health-row";
    row3.innerHTML = `<span>SLA 위반</span><strong>${row.slaBreachCount ?? 0}</strong>`;

    const row4 = document.createElement("div");
    row4.className = "health-row";
    row4.innerHTML = `<span>마지막 실행</span><strong>${relativeTime(row.lastExecutedAt)}</strong>`;

    card.appendChild(code);
    card.appendChild(rateWrap);
    card.appendChild(row1);
    card.appendChild(row2);
    card.appendChild(row3);
    card.appendChild(row4);

    card.addEventListener("click", async () => {
      if (!row.interfaceCode) return;
      selectedInterfaceCode = row.interfaceCode;
      const select = $("interfaceSelect");
      if (select) select.value = selectedInterfaceCode;
      await loadInterfaceDetail();
      showPanel("interfaces");
    });

    grid.appendChild(card);
  });
}

function inferFailStage(errorCode) {
  const code = (errorCode || "").toUpperCase();
  if (/AUTH|TOKEN|FORBIDDEN|UNAUTHORIZED/.test(code)) return 2;
  if (/TIMEOUT|REST|SOAP|MQ|SFTP|CONNECT|EXTERNAL/.test(code)) return 3;
  return 4;
}

function buildFlow(execution) {
  const defs = [
    "요청 수신",
    "인증/정책 검증",
    "외부 연계 호출",
    "응답 파싱/후처리",
    "최종 반영",
  ];

  if (!execution) {
    return {
      progress: 0,
      stages: defs.map((name, index) => ({ index: index + 1, name, state: "pending", duration: "-" })),
    };
  }

  const status = execution.status;
  const total = Math.max(Number(execution.latencyMillis || 0), 1);
  const buckets = [0.08, 0.14, 0.5, 0.18, 0.1].map((w) => `${Math.max(Math.round(total * w), 1)}ms`);

  let runningIndex = 3;
  if (status === "RUNNING") {
    const started = parseIso(execution.startedAt)?.getTime() || Date.now();
    const elapsed = Math.max(Date.now() - started, 1);
    const ratio = Math.min(elapsed / Math.max(total, elapsed), 1);
    if (ratio < 0.2) runningIndex = 1;
    else if (ratio < 0.4) runningIndex = 2;
    else if (ratio < 0.75) runningIndex = 3;
    else runningIndex = 4;
  }

  const failIndex = status === "FAILED" || status === "CANCELLED" ? inferFailStage(execution.errorCode) : -1;

  const stages = defs.map((name, i) => {
    const index = i + 1;
    let state = "pending";

    if (status === "SUCCESS") state = "success";
    if (status === "RUNNING") {
      if (index < runningIndex) state = "success";
      else if (index === runningIndex) state = "running";
    }
    if (failIndex > 0) {
      if (index < failIndex) state = "success";
      else if (index === failIndex) state = "fail";
    }

    return {
      index,
      name,
      state,
      duration: state === "pending" ? "-" : buckets[i],
    };
  });

  let progress = 0;
  if (status === "SUCCESS") progress = 100;
  else if (status === "RUNNING") progress = Math.min(20 * runningIndex - 10, 90);
  else if (failIndex > 0) progress = Math.min(20 * failIndex, 95);

  return { progress, stages };
}

function renderFlow() {
  const target = getCurrentHistory();
  const flow = buildFlow(target);
  const flowStages = $("flowStages");
  const progressBar = $("flowProgressBar");

  setText("flowExecutionId", target?.executionId || "-");
  setText("flowStatus", statusText(target?.status || "-"));

  const retryCount = target
    ? cachedRetryTasks.filter((task) => task.originalExecutionId === target.executionId).length
    : 0;
  const retryLatest = target
    ? cachedRetryTasks.find((task) => task.originalExecutionId === target.executionId)
    : null;
  setText("flowRetry", retryCount > 0 ? `${retryCount}회 (${retryLatest?.status || "-"})` : "없음");

  if (progressBar) {
    progressBar.style.width = `${flow.progress}%`;
  }

  if (!flowStages) return;
  flowStages.innerHTML = "";

  flow.stages.forEach((stage, idx) => {
    const step = document.createElement("div");
    step.className = `flow-step-box ${stage.state}`;
    step.title = `${stage.state} / ${stage.duration}`;

    const num = document.createElement("span");
    num.className = "flow-step-num";
    num.textContent = String(stage.index);

    const label = document.createElement("span");
    label.className = "flow-step-label";
    label.textContent = stage.name;

    step.appendChild(num);
    step.appendChild(label);
    flowStages.appendChild(step);

    if (idx < flow.stages.length - 1) {
      const sep = document.createElement("span");
      sep.className = "flow-sep";
      sep.textContent = "›";
      flowStages.appendChild(sep);
    }
  });
}

function makeIdempotencyKey() {
  const d = new Date();
  const stamp = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}${String(d.getHours()).padStart(2, "0")}${String(d.getMinutes()).padStart(2, "0")}${String(d.getSeconds()).padStart(2, "0")}`;
  return `manual-${stamp}-${Math.random().toString(36).slice(2, 8)}`;
}

function setPollingUi() {
  setText("pollStatus", `상태: ${polling ? "동작중" : "중지"} (${pollIntervalMs / 1000}초)`);
  setValue("pollStatusInput", polling ? `동작중 (${pollIntervalMs / 1000}초)` : "중지");
  setText("pollToggleBtn", polling ? "폴링 중지" : "폴링 시작");
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  polling = false;
  setPollingUi();
}

// function startPolling() {
//   stopPolling();
//   polling = true;
//   pollTimer = setInterval(async () => {
//     try {
//       await refreshRuntimeData(true);
//     } catch (_error) {
//       // 폴링 구간은 화면 메시지 과다를 막기 위해 무시
//     }
//   }, pollIntervalMs);
//   setPollingUi();
// }

function startPolling() {
  // 임시 중지: 자동 폴링 때문에 아무것도 안 해도 API가 계속 호출됨
  stopPolling();
  polling = false;
  setPollingUi();
  return;

  // pollTimer = setInterval(async () => {
  //   try {
  //     await refreshRuntimeData(true);
  //   } catch (_error) {
  //     // 폴링 구간은 화면 메시지 과다를 막기 위해 무시
  //   }
  // }, pollIntervalMs);
}

async function refreshRuntimeData(showLog = false) {
  await Promise.all([loadHistories(showLog), loadRetryTasks(showLog)]);
  renderFlow();
  updateLastUpdated();
}

async function validateStoredSession() {
  console.log("validateStoredSession 임시 통과");
  return true;

  // if (!accessToken) return false;
  // try {
  //   // 인증이 필요한 엔드포인트로 토큰 유효성 검증 (사용자 계정이 DB에 존재하는지 확인)
  //   // suppressAutoLogout=true: 여기서 401이 와도 sessionExpiredLogout()을 호출하지 않음
  //   await apiRequest("GET", "/api/v1/interfaces?size=1&page=0", undefined, true, true);
  //   return true;
  // } catch (_error) {
  //   return false;
  // }
}

async function checkConnection() {
  try {
    const health = await apiGet("/actuator/health");
    const status = (health && health.status) || "UNKNOWN";
    const text = status === "UP" ? "연결됨" : `연결됨(${status})`;
    setFieldText("connectionStatus", text);
    setFieldText("connectionStatusDash", text);
    showToast("연결 확인 성공", "success");
  } catch (error) {
    setFieldText("connectionStatus", "연결 실패");
    setFieldText("connectionStatusDash", "연결 실패");
    showOutput(error);
    showToast(`연결 실패: ${getErrorMessage(error)}`, "error");
  }
}

// async function login() {
//   try {
//     const username = $("username")?.value?.trim() || "";
//     const password = $("password")?.value || "";
//
//     const data = await apiPost("/api/v1/auth/login", {
//       username: username,
//       password: password,
//     });
//
//     // 토큰과 사용자 정보를 즉시 설정 (다른 API 호출 전)
//     accessToken = data.accessToken || "";
//     if (accessToken) {
//       localStorage.setItem("interfacehub.jwt", accessToken);
//     }
//     currentUsername = username;
//
//     showOutput(data);
//     showToast("로그인 성공", "success");
//
//     // 대시보드 보기 전환
//     showDashboardView();
//
//     // 데이터 로딩 병렬화
//     await Promise.all([
//       reloadDashboard(dashboardWindowHours),
//       loadIncidentSummary(dashboardWindowHours),
//       loadInterfaceOptions(),
//       flushPendingApiLogs()
//     ]);
//   } catch (error) {
//     showOutput(error);
//     showToast(`로그인 실패: ${getErrorMessage(error)}`, "error");
//   }
// }

  async function login() {
    try {
      const username = $("username")?.value?.trim() || "";
      const password = $("password")?.value || "";

      const data = await apiPost("/api/v1/auth/login", {
        username: username,
        password: password,
      });

      accessToken = data.accessToken || "";
      if (accessToken) {
        localStorage.setItem("interfacehub.jwt", accessToken);
      }

      currentUsername = username;

      showOutput(data);
      showToast("로그인 성공", "success");

      // 로그인 성공하면 먼저 화면 전환
      showDashboardView();

      // 각각 따로 실행해서 하나가 실패해도 로그인 전체가 실패하지 않게 처리
      // reloadDashboard(dashboardWindowHours)
      //     .catch((e) => {
      //       console.error("dashboard load failed", e);
      //       showOutput(e);
      //     });

      loadIncidentSummary(dashboardWindowHours)
          .catch((e) => {
            console.error("incident summary load failed", e);
            showOutput(e);
          });

      // loadInterfaceOptions()
      //     .catch((e) => {
      //       console.error("interface options load failed", e);
      //       showOutput(e);
      //     });

      flushPendingApiLogs()
          .catch((e) => {
            console.warn("pending log flush failed", e);
          });

    } catch (error) {
      showOutput(error);
      showToast(`로그인 실패: ${getErrorMessage(error)}`, "error");
    }
  }

function sessionExpiredLogout() {
  stopPolling();

  accessToken = "";
  currentUsername = "";
  localStorage.removeItem("interfacehub.jwt");
  showToast("세션이 만료되었습니다. 다시 로그인해주세요.", "error");
  syncDashboardFields();
  showLoginView();
}

function logout() {
  stopPolling();

  accessToken = "";
  currentUsername = "";
  localStorage.removeItem("interfacehub.jwt");
  showToast("로그아웃 완료", "success");
  syncDashboardFields();
  showLoginView();
}

function currentActor() {
  const u = (currentUsername || $("username")?.value || "").trim();
  return u ? u : "system";
}

async function loadInterfaceOptions() {
  try {
    const select = $("interfaceSelect");
    if (select) {
      select.innerHTML = "";
      const loading = document.createElement("option");
      loading.value = "";
      loading.textContent = "로딩 중...";
      select.appendChild(loading);
      select.disabled = true;
    }

    const interfaces = await apiGet("/api/v1/interfaces", true);
    cachedInterfaces = interfaces || [];
    if (!select) return;

    select.innerHTML = "";
    select.disabled = false;
    (interfaces || []).forEach((item) => {
      const option = document.createElement("option");
      option.value = item.interfaceCode;
      option.textContent = `${item.interfaceCode} | ${item.name}`;
      select.appendChild(option);
    });

    if ((interfaces || []).length > 0) {
      selectedInterfaceCode = interfaces[0].interfaceCode;
      select.value = selectedInterfaceCode;
      await loadInterfaceDetail();
    } else {
      selectedInterfaceCode = "";
      const option = document.createElement("option");
      option.value = "";
      option.textContent = "인터페이스 데이터가 없습니다";
      select.appendChild(option);
      select.disabled = true;
      showToast("인터페이스 목록이 비어 있습니다. (DB/seed 프로필을 확인하세요)", "error");
    }
  } catch (error) {
    const select = $("interfaceSelect");
    if (select) {
      select.innerHTML = "";
      const option = document.createElement("option");
      option.value = "";
      option.textContent = "인터페이스 조회 실패";
      select.appendChild(option);
      select.disabled = true;
    }
    showOutput(error);
    showToast(`인터페이스 목록 조회 실패: ${getErrorMessage(error)}`, "error");
  }
}

async function loadInterfaceDetail() {
  if (!selectedInterfaceCode) return;
  try {
    const detail = await apiGet(`/api/v1/interfaces/${encodeURIComponent(selectedInterfaceCode)}`, true);
    renderBasicInfo(detail);
    await Promise.all([loadConfigs(false), refreshRuntimeData(false)]);
    showToast("상세 정보 갱신 완료", "success");
  } catch (error) {
    showOutput(error);
    showToast(`상세 조회 실패: ${getErrorMessage(error)}`, "error");
  }
}

async function loadConfigs(showLog = true) {
  if (!selectedInterfaceCode) return;
  const data = await apiGet(`/api/v1/interfaces/${encodeURIComponent(selectedInterfaceCode)}/configs?page=0&size=20`, true);
  renderConfigs(data);
  if (showLog) showOutput(data);
}

async function loadHistories(showLog = true) {
  if (!selectedInterfaceCode) return;
  const data = await apiGet(`/api/v1/interfaces/${encodeURIComponent(selectedInterfaceCode)}/histories?page=0&size=80`, true);
  cachedHistories = data.content || [];

  if (selectedExecutionId && !cachedHistories.some((item) => item.executionId === selectedExecutionId)) {
    selectedExecutionId = "";
  }

  renderHistories();
  renderFlow();
  if (showLog) showOutput(data);
}

async function loadRetryTasks(showLog = true) {
  const data = await apiGet("/api/v1/retries?page=0&size=100", true);
  cachedRetryTasks = data.content || [];
  renderRetryTasks();
  if (showLog) showOutput(data);
}

function subscribeExecution(interfaceCode, onResult) {
  const url = `${baseUrl()}/api/v1/interfaces/${encodeURIComponent(interfaceCode)}/executions/stream`;

  // EventSource cannot send Authorization headers. If the app uses JWT (Bearer), use fetch streaming instead.
  if (accessToken) {
    return subscribeExecutionWithFetch(url, onResult);
  }

  const es = new EventSource(url);
  es.addEventListener("execution-completed", (e) => {
    try {
      const parsed = JSON.parse(e.data);
      Promise.resolve(onResult(parsed))
        .then((consumed) => {
          if (consumed) es.close();
        })
        .catch(() => es.close());
    } catch (error) {
      es.close();
    }
  });
  es.onerror = () => es.close();
  return { close: () => es.close() };
}

function subscribeExecutionWithFetch(url, onResult) {
  const controller = new AbortController();
  const decoder = new TextDecoder();
  let closed = false;

  function close() {
    closed = true;
    controller.abort();
  }

  function handleSseBlock(block) {
    const lines = block.split("\n");
    let eventName = "message";
    const dataLines = [];

    for (const line of lines) {
      if (!line) continue;
      if (line.startsWith(":")) continue;
      const idx = line.indexOf(":");
      const field = idx >= 0 ? line.substring(0, idx) : line;
      const value = idx >= 0 ? line.substring(idx + 1).trimStart() : "";
      if (field === "event") eventName = value;
      if (field === "data") dataLines.push(value);
    }

    if (eventName !== "execution-completed") return;
    const dataText = dataLines.join("\n");
    if (!dataText) return;

    const parsed = JSON.parse(dataText);
    Promise.resolve(onResult(parsed))
      .then((consumed) => {
        if (consumed) close();
      })
      .catch(() => close());
  }

  (async () => {
    try {
      const response = await fetch(url, {
        method: "GET",
        headers: { Authorization: `Bearer ${accessToken}` },
        signal: controller.signal,
      });
      if (!response.ok || !response.body) {
        throw new Error(`SSE subscribe failed: ${response.status}`);
      }

      const reader = response.body.getReader();
      let buffer = "";
      while (!closed) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        let idx;
        while ((idx = buffer.indexOf("\n\n")) >= 0) {
          const block = buffer.substring(0, idx).trimEnd();
          buffer = buffer.substring(idx + 2);
          if (block) handleSseBlock(block);
          if (closed) break;
        }
      }
    } catch (error) {
      // Ignore subscription errors (execution itself is handled by the normal API call).
    }
  })();

  return { close };
}

async function executeInterface() {
  if (!selectedInterfaceCode) {
    showToast("인터페이스를 먼저 선택하세요.", "error");
    return;
  }

  let expectedExecutionId = "";
  const stream = subscribeExecution(selectedInterfaceCode, (result) => {
    if (expectedExecutionId && result?.executionId && result.executionId !== expectedExecutionId) {
      return false;
    }
    $("execResultBox").textContent = JSON.stringify(result, null, 2);
    selectedExecutionId = result.executionId || selectedExecutionId;
    showOutput(result);
    showToast(`Execution completed: ${result.status}`, result.status === "SUCCESS" ? "success" : "error");
    refreshRuntimeData(false);
    return true;
  });

  try {
    const idempotencyKey = $("execIdempotencyKey").value.trim() || makeIdempotencyKey();
    $("execIdempotencyKey").value = idempotencyKey;

    const request = {
      idempotencyKey,
      payload: parseJsonInput("execPayload", {}),
      environment: $("execEnvironment").value || null,
      clientId: $("execClientId").value.trim() || null,
      clientSecret: $("execClientSecret").value.trim() || null,
      apiKey: $("execApiKey").value.trim() || null,
      partnerId: $("execPartnerId").value.trim() || null,
    };

    const data = await apiPost(`/api/v1/interfaces/${encodeURIComponent(selectedInterfaceCode)}/execute`, request);
    $("execResultBox").textContent = JSON.stringify(data, null, 2);
    expectedExecutionId = data.executionId || expectedExecutionId;
    selectedExecutionId = data.executionId || selectedExecutionId;

    showOutput(data);
    showToast("수동 실행 완료", "success");
    await refreshRuntimeData(false);
  } catch (error) {
    stream.close();
    showOutput(error);
    showToast(`실행 실패: ${getErrorMessage(error)}`, "error");
  }
}

async function publishConfig() {
  if (!selectedInterfaceCode) return;

  const configId = $("publishConfigId").value;
  const actor = $("publishActor").value.trim() || "operator1";
  if (!configId) {
    showToast("배포할 설정 ID를 입력하세요.", "error");
    return;
  }

  try {
    const data = await apiPost(
      `/api/v1/interfaces/${encodeURIComponent(selectedInterfaceCode)}/configs/${configId}/publish?actor=${encodeURIComponent(actor)}`,
      {}
    );
    showOutput(data);
    showToast("설정 배포 완료", "success");
    await loadConfigs(false);
  } catch (error) {
    showOutput(error);
    showToast(`설정 배포 실패: ${getErrorMessage(error)}`, "error");
  }
}

function readRetryStrategy() {
  const maxAttempts = Math.max(Number($("retryMaxAttempts").value || 1), 1);
  const baseDelayMs = Math.max(Number($("retryDelayMs").value || 0), 0);
  return {
    maxAttempts,
    baseDelayMs,
    backoffType: $("retryBackoffType").value || "NONE",
    condition: $("retryCondition").value || "ALWAYS",
    requester: $("retryRequester").value.trim() || "operator1",
    actor: $("retryActor").value.trim() || "manager1",
    reasonCode: $("retryReasonCode").value.trim() || "MANUAL_RETRY",
    reasonDetail: $("retryReasonDetail").value.trim() || "운영자 수동 재처리",
  };
}

function shouldRetryByCondition(history, condition) {
  const category = classifyErrorCategory(history?.errorCode, history?.errorMessage);
  if (condition === "ALWAYS") return true;
  if (condition === "NETWORK_ONLY") return category === "NETWORK";
  if (condition === "NON_BUSINESS") return category !== "BUSINESS";
  return true;
}

function attemptDelayMs(baseDelayMs, backoffType, attemptNo) {
  if (backoffType === "EXPONENTIAL") return baseDelayMs * Math.pow(2, attemptNo - 1);
  if (backoffType === "LINEAR") return baseDelayMs * attemptNo;
  return baseDelayMs;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function showConfirm(title, message) {
  return new Promise((resolve) => {
    const modal = $("confirmModal");
    const okBtn = $("confirmOkBtn");
    const cancelBtn = $("confirmCancelBtn");
    if (!modal || !okBtn || !cancelBtn) {
      resolve(true);
      return;
    }

    setText("confirmTitle", title);
    setText("confirmMessage", message);
    modal.classList.remove("hidden");

    const cleanup = (result) => {
      modal.classList.add("hidden");
      okBtn.removeEventListener("click", onOk);
      cancelBtn.removeEventListener("click", onCancel);
      resolve(result);
    };

    const onOk = () => cleanup(true);
    const onCancel = () => cleanup(false);

    okBtn.addEventListener("click", onOk);
    cancelBtn.addEventListener("click", onCancel);
  });
}

async function createRetryTask(executionId, strategy) {
  return apiPost(`/api/v1/interfaces/${encodeURIComponent(selectedInterfaceCode)}/retries`, {
    originalExecutionId: executionId,
    requester: strategy.requester,
    reasonCode: strategy.reasonCode,
    reasonDetail: strategy.reasonDetail,
  });
}

async function runRetryTask(taskId, strategy) {
  if (!accessToken) {
    return { mode: "REQUEST_ONLY" };
  }
  await apiPost(`/api/v1/retries/${taskId}/approve`, { approver: strategy.actor }, true);
  const execution = await apiPost(`/api/v1/retries/${taskId}/execute`, {}, true);
  return { mode: "EXECUTED", execution };
}

function shouldRetryRequestError(error) {
  if (!error) return false;
  if (typeof error.status === "number") {
    return error.status >= 500;
  }
  const message = String(error.message || "");
  return /failed to fetch|networkerror|timeout|econnreset|etimedout/i.test(message);
}

async function retryWithStrategy(executionId, strategy) {
  const history = cachedHistories.find((item) => item.executionId === executionId);
  if (!history) {
    return { executionId, success: false, reason: "대상 실행 이력을 찾지 못했습니다." };
  }

  if (!shouldRetryByCondition(history, strategy.condition)) {
    return { executionId, success: false, skipped: true, reason: "선택한 조건과 맞지 않아 재처리를 생략했습니다." };
  }

  let lastError = null;
  let retryTaskId = null;
  for (let attempt = 1; attempt <= strategy.maxAttempts; attempt += 1) {
    try {
      if (!retryTaskId) {
        const retryTask = await createRetryTask(executionId, strategy);
        retryTaskId = retryTask?.id ?? null;
      }
      if (!retryTaskId) {
        return { executionId, success: false, reason: "재처리 요청 생성에 실패했습니다.(retryTaskId 없음)" };
      }

      const runResult = await runRetryTask(retryTaskId, strategy);
      await refreshRuntimeData(false);
      if (runResult.execution?.executionId) {
        selectedExecutionId = runResult.execution.executionId;
      }

      if (runResult.mode === "EXECUTED" && runResult.execution?.status && runResult.execution.status !== "SUCCESS") {
        const code = runResult.execution.errorCode ? ` (${runResult.execution.errorCode})` : "";
        return {
          executionId,
          success: false,
          attempt,
          retryTaskId,
          mode: runResult.mode,
          newExecutionId: runResult.execution?.executionId || null,
          reason: `재처리 실행 결과: ${runResult.execution.status}${code}`,
        };
      }

      return {
        executionId,
        success: true,
        attempt,
        retryTaskId,
        mode: runResult.mode,
        newExecutionId: runResult.execution?.executionId || null,
      };
    } catch (error) {
      lastError = getErrorMessage(error);
      if (!shouldRetryRequestError(error)) {
        return { executionId, success: false, retryTaskId, reason: lastError };
      }
      if (attempt < strategy.maxAttempts) {
        const delayMs = attemptDelayMs(strategy.baseDelayMs, strategy.backoffType, attempt);
        if (delayMs > 0) {
          await sleep(delayMs);
        }
      }
    }
  }

  return {
    executionId,
    success: false,
    retryTaskId,
    reason: `최대 재시도(${strategy.maxAttempts}) 초과: ${lastError || "원인 미상"}`,
  };
}

async function singleRetry() {
  const current = getCurrentHistory();
  if (!current) {
    showToast("재처리 대상 실행을 먼저 선택하세요.", "error");
    return;
  }

  const ok = await showConfirm("단건 재처리", `실행 ${current.executionId} 를 재처리하시겠습니까?`);
  if (!ok) return;

  try {
    const result = await retryWithStrategy(current.executionId, readRetryStrategy());
    showOutput(result);
    if (result.success) {
      showToast(result.mode === "REQUEST_ONLY" ? "재처리 요청 생성 완료(승인/실행은 로그인 후 가능)" : "재처리 완료", "success");
    } else if (result.skipped) {
      showToast(result.reason, "error");
    } else {
      showToast(result.reason, "error");
    }
  } catch (error) {
    showOutput(error);
    showToast(`재처리 실패: ${getErrorMessage(error)}`, "error");
  }
}

async function bulkRetry() {
  const ids = getSelectedHistoryIds();
  if (ids.length === 0) {
    showToast("체크박스로 실패 건을 선택하세요.", "error");
    return;
  }

  const ok = await showConfirm("대량 재처리", `선택한 ${ids.length}건을 재처리할까요?`);
  if (!ok) return;

  const strategy = readRetryStrategy();
  const results = [];

  for (const executionId of ids) {
    const result = await retryWithStrategy(executionId, strategy);
    results.push(result);
  }

  const successCount = results.filter((r) => r.success).length;
  const failCount = results.length - successCount;
  showOutput({ summary: { total: results.length, success: successCount, fail: failCount }, results });

  if (failCount === 0) {
    showToast(`Bulk Retry 완료: ${successCount}건 성공`, "success");
  } else {
    showToast(`Bulk Retry 완료: 성공 ${successCount}건 / 실패 ${failCount}건`, "error");
  }
}

function normalizeSearchType(type) {
  if (type === "INTERFACE") return "인터페이스";
  if (type === "CONFIG") return "설정";
  if (type === "HISTORY") return "실행";
  if (type === "RETRY") return "재처리";
  return type || "기타";
}

function closeGlobalSearchResults() {
  const box = $("globalSearchResults");
  if (!box) return;
  box.classList.add("hidden");
  box.innerHTML = "";
}

async function openGlobalSearchItem(item) {
  if (!item) return;
  closeGlobalSearchResults();

  if (item.type === "INTERFACE") {
    if (item.interfaceCode) {
      selectedInterfaceCode = item.interfaceCode;
      const select = $("interfaceSelect");
      if (select) select.value = selectedInterfaceCode;
      await loadInterfaceDetail();
      showPanel("interfaces");
    }
    return;
  }

  if (item.type === "CONFIG") {
    if (item.interfaceCode) {
      selectedInterfaceCode = item.interfaceCode;
      const select = $("interfaceSelect");
      if (select) select.value = selectedInterfaceCode;
      await loadInterfaceDetail();
    }
    showPanel("configs");
    return;
  }

  if (item.type === "HISTORY") {
    if (item.interfaceCode) {
      selectedInterfaceCode = item.interfaceCode;
      const select = $("interfaceSelect");
      if (select) select.value = selectedInterfaceCode;
      await loadInterfaceDetail();
    }
    showPanel("executions");
    if (item.executionId) {
      await openExecutionDetail(item.executionId);
    }
    return;
  }

  if (item.type === "RETRY") {
    if (item.interfaceCode) {
      selectedInterfaceCode = item.interfaceCode;
      const select = $("interfaceSelect");
      if (select) select.value = selectedInterfaceCode;
      await loadInterfaceDetail();
    }
    showPanel("retries");
  }
}

function renderGlobalSearchResults(query, items) {
  const box = $("globalSearchResults");
  if (!box) return;

  const list = items || [];
  if (list.length === 0) {
    box.classList.remove("hidden");
    box.innerHTML = `<div class="search-results-header">"${query}" 검색 결과 없음</div>`;
    return;
  }

  box.classList.remove("hidden");
  box.innerHTML = `<div class="search-results-header">"${query}" 결과 ${list.length}건 (클릭하면 이동)</div>`;

  list.forEach((item) => {
    const row = document.createElement("div");
    row.className = "search-item";
    row.addEventListener("click", () => openGlobalSearchItem(item).catch((e) => showOutput(e)));

    const type = document.createElement("div");
    type.className = "search-type";
    type.textContent = normalizeSearchType(item.type);

    const content = document.createElement("div");
    const title = document.createElement("div");
    title.className = "search-title";
    title.textContent = item.title || "-";
    const subtitle = document.createElement("div");
    subtitle.className = "search-subtitle";
    subtitle.textContent = item.subtitle || "";
    content.appendChild(title);
    content.appendChild(subtitle);

    row.appendChild(type);
    row.appendChild(content);
    box.appendChild(row);
  });
}

async function runGlobalSearch() {
  const raw = $("globalSearchInput")?.value || "";
  const q = raw.trim();
  if (!q) {
    closeGlobalSearchResults();
    return;
  }

  const seq = (globalSearchSeq += 1);

  // UX: 즉시 로컬(인터페이스) 후보를 보여주고, 서버 통합 검색으로 확장
  const local = (cachedInterfaces || [])
    .filter((i) => `${i.interfaceCode} ${i.name} ${i.ownerTeam || ""} ${i.businessCategory || ""} ${i.externalOrg || ""}`
      .toLowerCase()
      .includes(q.toLowerCase()))
    .slice(0, 5)
    .map((i) => ({
      type: "INTERFACE",
      title: `${i.interfaceCode} | ${i.name}`,
      subtitle: `${i.protocolType || ""} / ${i.status || ""}`,
      interfaceCode: i.interfaceCode,
    }));

  renderGlobalSearchResults(q, local);

  try {
    const data = await apiGet(`/api/v1/search${queryString({ q, limit: 15 })}`, true);
    if (seq !== globalSearchSeq) {
      return;
    }
    const items = data?.items || [];
    const merged = [...local, ...items].slice(0, 20);
    renderGlobalSearchResults(q, merged);
    showOutput({ globalSearch: { q, count: merged.length }, raw: data });
  } catch (error) {
    if (seq !== globalSearchSeq) {
      return;
    }
    showOutput(error);
    showToast(`전체 검색 실패: ${getErrorMessage(error)}`, "error");
  }
}

async function loadApiLogs(page = 0) {
  try {
    const data = await apiGet(`/api/v1/debug-logs?page=${page}&size=50`, true);
    const rows = data?.content || [];
    cachedApiLogs = rows
      .map((r) => ({
        occurredAt: r.occurredAt,
        createdAt: r.createdAt,
        method: r.method,
        path: r.path,
        responseStatus: r.responseStatus,
        errorMessage: r.errorMessage,
        durationMs: r.durationMs,
      }))
      .slice(0, 200);
    renderApiLogBox(cachedApiLogs);
    showToast("API 로그 조회 완료", "success");
    showOutput(data);
  } catch (error) {
    showOutput(error);
    showToast(`API 로그 조회 실패: ${getErrorMessage(error)}`, "error");
  }
}

function renderDlqMessages(pageData) {
  const tbody = $("dlqTable")?.querySelector("tbody");
  if (!tbody) return;
  tbody.innerHTML = "";
  (pageData?.content || []).forEach((row) => {
    const tr = document.createElement("tr");
    const tdSel = document.createElement("td");
    const cb = document.createElement("input");
    cb.type = "checkbox";
    cb.className = "dlq-row-select";
    cb.dataset.dlqId = String(row.id);
    cb.addEventListener("change", () => syncDlqSelectAll());
    tdSel.appendChild(cb);
    tr.appendChild(tdSel);

    [row.id, row.interfaceCode, row.topic, row.reason, row.replayCount ?? 0, formatDateTime(row.createdAt)].forEach((v) => {
      const td = document.createElement("td");
      td.textContent = v == null ? "-" : String(v);
      tr.appendChild(td);
    });
    tbody.appendChild(tr);
  });
}

async function loadDlqMessages() {
  try {
    const interfaceCode = ($("dlqFilterCode")?.value || "").trim() || null;
    const page = Number($("dlqFilterPage")?.value || 0);
    const size = Number($("dlqFilterSize")?.value || 20);
    dlqState = { ...dlqState, page, size, interfaceCode: interfaceCode || "" };

    const data = await apiGet(`/api/v1/dlq${queryString({ page, size, interfaceCode })}`, true);
    renderDlqMessages(data);
    dlqState.totalPages = data?.totalPages ?? 0;
    dlqState.totalElements = data?.totalElements ?? 0;
    setText("dlqPageInfo", `page=${data?.pageNumber ?? page} / totalPages=${data?.totalPages ?? "-"} / total=${data?.totalElements ?? "-"}`);
    setValue("dlqFilterPage", data?.pageNumber ?? page);
    syncDlqSelectAll();
    showOutput(data);
  } catch (e) {
    showOutput(e);
    showToast(`DLQ 조회 실패: ${getErrorMessage(e)}`, "error");
  }
}

function renderDlqReplayRequests(pageData) {
  const tbody = $("dlqReplayTable")?.querySelector("tbody");
  if (!tbody) return;
  tbody.innerHTML = "";
  (pageData?.content || []).forEach((row) => {
    const tr = document.createElement("tr");
    const tdSel = document.createElement("td");
    const cb = document.createElement("input");
    cb.type = "checkbox";
    cb.className = "dlq-replay-row-select";
    cb.dataset.replayId = String(row.id);
    cb.addEventListener("change", () => syncDlqReplaySelectionUi());
    tdSel.appendChild(cb);
    tr.appendChild(tdSel);

    [row.id, row.dlqId, row.interfaceCode, row.status, row.requester, row.approver, formatDateTime(row.createdAt)].forEach((v) => {
      const td = document.createElement("td");
      td.textContent = v == null ? "-" : String(v);
      tr.appendChild(td);
    });
    tbody.appendChild(tr);
  });
}

async function loadDlqReplayRequests() {
  try {
    const status = ($("dlqReplayStatusFilter")?.value || "").trim() || null;
    const fromDate = $("dlqReplayFrom")?.value || null;
    const toDate = $("dlqReplayTo")?.value || null;
    const page = dlqReplayState.page ?? 0;
    const size = dlqReplayState.size ?? 20;
    dlqReplayState = { ...dlqReplayState, status: status || "", from: fromDate || "", to: toDate || "" };

    const data = await apiGet(`/api/v1/dlq/replay-requests${queryString({ page, size, status, fromDate, toDate })}`, true);
    renderDlqReplayRequests(data);
    dlqReplayState.totalPages = data?.totalPages ?? 0;
    dlqReplayState.totalElements = data?.totalElements ?? 0;
    syncDlqReplaySelectionUi();
    await refreshDlqReplayWorkflowCounts(fromDate, toDate);
    showOutput(data);
  } catch (e) {
    showOutput(e);
    showToast(`DLQ 리플레이 요청 조회 실패: ${getErrorMessage(e)}`, "error");
  }
}

function dlqSelectedIds() {
  return Array.from(document.querySelectorAll(".dlq-row-select"))
    .filter((cb) => cb.checked)
    .map((cb) => Number(cb.dataset.dlqId))
    .filter((v) => Number.isFinite(v));
}

function dlqReplaySelectedIds() {
  return Array.from(document.querySelectorAll(".dlq-replay-row-select"))
    .filter((cb) => cb.checked)
    .map((cb) => Number(cb.dataset.replayId))
    .filter((v) => Number.isFinite(v));
}

function syncDlqSelectAll() {
  const all = $("dlqSelectAll");
  const rows = Array.from(document.querySelectorAll(".dlq-row-select"));
  if (!all) return;
  if (rows.length === 0) {
    all.checked = false;
    all.indeterminate = false;
    return;
  }
  const checked = rows.filter((cb) => cb.checked).length;
  all.checked = checked === rows.length;
  all.indeterminate = checked > 0 && checked < rows.length;
}

function toggleDlqSelectAll(checked) {
  document.querySelectorAll(".dlq-row-select").forEach((cb) => (cb.checked = checked));
  syncDlqSelectAll();
}

function syncDlqReplaySelectionUi() {
  const ids = dlqReplaySelectedIds();
  setText("dlqReplaySelectedCount", String(ids.length));
  const banner = $("dlqReplayActions");
  if (banner) {
    if (ids.length > 0) banner.classList.remove("hidden");
    else banner.classList.add("hidden");
  }

  const all = $("dlqReplaySelectAll");
  const rows = Array.from(document.querySelectorAll(".dlq-replay-row-select"));
  if (all) {
    if (rows.length === 0) {
      all.checked = false;
      all.indeterminate = false;
    } else {
      const checked = rows.filter((cb) => cb.checked).length;
      all.checked = checked === rows.length;
      all.indeterminate = checked > 0 && checked < rows.length;
    }
  }
}

function toggleDlqReplaySelectAll(checked) {
  document.querySelectorAll(".dlq-replay-row-select").forEach((cb) => (cb.checked = checked));
  syncDlqReplaySelectionUi();
}

async function dlqCreateReplay() {
  const ids = dlqSelectedIds();
  if (ids.length === 0) {
    showToast("선택된 DLQ 메시지가 없습니다.", "error");
    return;
  }
  const actor = currentActor();
  const reasonDetail = `UI replay request by ${actor}`;

  let ok = 0;
  let fail = 0;
  for (const id of ids) {
    try {
      await apiPost(`/api/v1/dlq/${encodeURIComponent(id)}/replay-requests`, {
        requester: actor,
        reasonCode: "MANUAL",
        reasonDetail,
        payloadOverride: null,
      }, true);
      ok++;
    } catch (e) {
      fail++;
      showOutput(e);
    }
  }
  showToast(`리플레이 요청 완료 (ok=${ok}, fail=${fail})`, fail > 0 ? "error" : "success");
  await loadDlqMessages();
  dlqReplayState.page = 0;
  await loadDlqReplayRequests();
}

async function dlqReplayApprove() {
  const ids = dlqReplaySelectedIds();
  if (ids.length === 0) return;
  const actor = currentActor();
  let ok = 0, fail = 0;
  for (const id of ids) {
    try {
      await apiPost(`/api/v1/dlq/replay-requests/${encodeURIComponent(id)}/approve`, { approver: actor }, true);
      ok++;
    } catch (e) {
      fail++;
      showOutput(e);
    }
  }
  showToast(`승인 처리 완료 (ok=${ok}, fail=${fail})`, fail > 0 ? "error" : "success");
  await loadDlqReplayRequests();
}

async function dlqReplayReject() {
  const ids = dlqReplaySelectedIds();
  if (ids.length === 0) return;
  const actor = currentActor();
  const reason = prompt("반려 사유를 입력하세요 (필수)", "") || "";
  if (!reason.trim()) {
    showToast("반려 사유는 필수입니다.", "error");
    return;
  }
  let ok = 0, fail = 0;
  for (const id of ids) {
    try {
      await apiPost(`/api/v1/dlq/replay-requests/${encodeURIComponent(id)}/reject`, { approver: actor, reason: reason.trim() }, true);
      ok++;
    } catch (e) {
      fail++;
      showOutput(e);
    }
  }
  showToast(`반려 처리 완료 (ok=${ok}, fail=${fail})`, fail > 0 ? "error" : "success");
  await loadDlqReplayRequests();
}

async function dlqReplayExecute() {
  const ids = dlqReplaySelectedIds();
  if (ids.length === 0) return;
  const actor = currentActor();
  let ok = 0, fail = 0;
  for (const id of ids) {
    try {
      await apiPost(`/api/v1/dlq/replay-requests/${encodeURIComponent(id)}/execute`, { executor: actor }, true);
      ok++;
    } catch (e) {
      fail++;
      showOutput(e);
    }
  }
  showToast(`실행 처리 완료 (ok=${ok}, fail=${fail})`, fail > 0 ? "error" : "success");
  await loadDlqReplayRequests();
}

async function refreshDlqReplayWorkflowCounts(fromDate, toDate) {
  const statuses = ["PENDING", "APPROVED", "EXECUTED", "FAILED"];
  const promises = statuses.map((status) => apiGet(`/api/v1/dlq/replay-requests${queryString({ page: 0, size: 1, status, fromDate: fromDate || null, toDate: toDate || null })}`, true)
    .then((d) => ({ status, total: d?.totalElements ?? 0 }))
    .catch((_e) => ({ status, total: null })));

  const results = await Promise.all(promises);
  const map = Object.fromEntries(results.map((r) => [r.status, r.total]));
  setText("wfPendingCount", map.PENDING == null ? "-" : String(map.PENDING));
  setText("wfApprovedCount", map.APPROVED == null ? "-" : String(map.APPROVED));
  setText("wfExecutedCount", map.EXECUTED == null ? "-" : String(map.EXECUTED));
  setText("wfFailedCount", map.FAILED == null ? "-" : String(map.FAILED));
}

function bindInterfaceSubtabs() {
  const buttons = Array.from(document.querySelectorAll("#interfaceSubtabs .subtab"));
  if (buttons.length === 0) return;
  buttons.forEach((btn) => {
    btn.addEventListener("click", () => {
      buttons.forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      const tab = btn.dataset.itab;
      $("itabInfo")?.classList.toggle("hidden", tab !== "info");
      $("itabSla")?.classList.toggle("hidden", tab !== "sla");
      $("itabExec")?.classList.toggle("hidden", tab !== "exec");
      if (tab === "sla") renderIfSlaTab().catch((e) => showOutput(e));
    });
  });
}

async function renderIfSlaTab() {
  if (!selectedInterfaceCode) return;
  const data = await apiGet(
    `/api/v1/interfaces/${encodeURIComponent(selectedInterfaceCode)}/histories?page=0&size=20`,
    true
  );
  const items = (data.content || []).slice(0, 20).reverse();

  const latencyEl = $("ifLatencyChart");
  if (latencyEl) {
    if (ifLatencyChart) ifLatencyChart.destroy();
    const labels = items.map((_, i) => `#${i + 1}`);
    const latencies = items.map((e) => e.latencyMillis ?? 0);
    ifLatencyChart = new Chart(latencyEl.getContext("2d"), {
      type: "line",
      data: {
        labels,
        datasets: [{
          label: "Latency (ms)",
          data: latencies,
          borderColor: "#8b5cf6",
          backgroundColor: "rgba(139, 92, 246, 0.1)",
          fill: true,
          tension: 0.4,
        }],
      },
      options: {
        responsive: true,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true } },
      },
    });
  }

  const donutEl = $("ifStatusDonut");
  if (donutEl) {
    if (ifStatusDonutChart) ifStatusDonutChart.destroy();
    const counts = { SUCCESS: 0, FAILED: 0, TIMEOUT: 0, CANCELLED: 0 };
    items.forEach((e) => {
      if (counts[e.status] != null) counts[e.status]++;
    });
    ifStatusDonutChart = new Chart(donutEl.getContext("2d"), {
      type: "doughnut",
      data: {
        labels: ["SUCCESS", "FAILED", "TIMEOUT", "CANCELLED"],
        datasets: [{
          data: [counts.SUCCESS, counts.FAILED, counts.TIMEOUT, counts.CANCELLED],
          backgroundColor: ["#24a36b", "#d35151", "#d79a2c", "#8998b0"],
          borderWidth: 0,
        }],
      },
      options: {
        responsive: true,
        plugins: { legend: { position: "bottom" } },
        cutout: "64%",
      },
    });
  }
}

function bindDlqSubtabs() {
  const buttons = Array.from(document.querySelectorAll("#panelDlq .subtab"));
  if (buttons.length === 0) return;
  buttons.forEach((btn) => {
    btn.addEventListener("click", () => {
      buttons.forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      const tab = btn.dataset.tab;
      $("tabDlqMessages")?.classList.toggle("hidden", tab !== "dlqMessages");
      $("tabDlqReplays")?.classList.toggle("hidden", tab !== "dlqReplays");
    });
  });
}

function renderSchedules(rows) {
  const tbody = $("schedulesTable")?.querySelector("tbody");
  if (!tbody) return;
  tbody.innerHTML = "";
  (rows || []).forEach((row) => {
    const tr = document.createElement("tr");

    const tdIf = document.createElement("td");
    tdIf.textContent = row.interfaceCode || "-";
    const tdCron = document.createElement("td");
    tdCron.textContent = row.cronExpression || "-";

    const tdEn = document.createElement("td");
    const enabledBtn = document.createElement("button");
    enabledBtn.className = `status-pill ${row.enabled ? "success" : "pending"}`;
    enabledBtn.style.cursor = "pointer";
    enabledBtn.textContent = row.enabled ? "활성" : "비활성";
    enabledBtn.addEventListener("click", async () => {
      try {
        await apiPatch(`/api/v1/schedules/${encodeURIComponent(row.interfaceCode)}/enabled?enabled=${!row.enabled}`, {}, true);
        await loadSchedules();
        showToast("스케줄 상태를 변경했습니다.", "success");
      } catch (e) {
        showOutput(e);
        showToast(`스케줄 변경 실패: ${getErrorMessage(e)}`, "error");
      }
    });
    tdEn.appendChild(enabledBtn);

    const tdPayload = document.createElement("td");
    tdPayload.textContent = truncateText(row.payloadTemplate, 30);
    tdPayload.title = row.payloadTemplate || "";

    const tdDate = document.createElement("td");
    tdDate.textContent = formatDateTime(row.createdAt);

    const tdAction = document.createElement("td");
    const delBtn = document.createElement("button");
    delBtn.className = "secondary";
    delBtn.textContent = "삭제";
    delBtn.addEventListener("click", () => deleteSchedule(row.interfaceCode));
    tdAction.appendChild(delBtn);

    tr.appendChild(tdIf);
    tr.appendChild(tdCron);
    tr.appendChild(tdEn);
    tr.appendChild(tdPayload);
    tr.appendChild(tdDate);
    tr.appendChild(tdAction);
    tbody.appendChild(tr);
  });
}

async function createSchedule() {
  const interfaceCode = $("schedCode")?.value?.trim();
  const cronExpression = $("schedCron")?.value?.trim();
  const payloadTemplate = $("schedPayload")?.value?.trim();

  if (!interfaceCode || !cronExpression) {
    showToast("인터페이스 코드와 Cron 표현식을 입력하세요.", "error");
    return;
  }

  try {
    const data = await apiPost("/api/v1/schedules", {
      interfaceCode,
      cronExpression,
      payloadTemplate
    }, true);
    showOutput(data);
    showToast("스케줄 등록 완료", "success");
    setValue("schedCode", "");
    setValue("schedCron", "");
    setValue("schedPayload", "{}");
    await loadSchedules();
  } catch (error) {
    showOutput(error);
    showToast(`스케줄 등록 실패: ${getErrorMessage(error)}`, "error");
  }
}

async function deleteSchedule(code) {
  const ok = await showConfirm("스케줄 삭제", `${code} 인터페이스의 스케줄을 삭제하시겠습니까?`);
  if (!ok) return;

  try {
    await apiRequest("DELETE", `/api/v1/schedules/${encodeURIComponent(code)}`, {}, true);
    showToast("스케줄 삭제 완료");
    await loadSchedules();
  } catch (error) {
    showOutput(error);
    showToast(`스케줄 삭제 실패: ${getErrorMessage(error)}`, "error");
  }
}

async function loadSchedules() {
  try {
    const data = await apiGet("/api/v1/schedules", true);
    renderSchedules(data);
    showOutput(data);
  } catch (e) {
    showOutput(e);
    showToast(`스케줄 조회 실패: ${getErrorMessage(e)}`, "error");
  }
}

function focusConfigSection() {
  showPanel("configs");
  showToast("설정(Config) 영역으로 이동했습니다.", "success");
}

function ignoreFailure() {
  const current = getCurrentHistory();
  if (!current) {
    showToast("선택된 실패 건이 없습니다.", "error");
    return;
  }
  showOutput({ executionId: current.executionId, action: "IGNORED", at: nowText() });
  showToast("Ignore 처리했습니다. 운영 정책에 맞게 후속 처리하세요.", "success");
}

function togglePolling() {
  if (polling) {
    stopPolling();
  } else {
    startPolling();
  }
}

function initializeHandlers() {
  bindClick("checkConnectionBtn", checkConnection);
  bindClick("checkConnectionBtnDash", checkConnection);
  bindClick("loginBtn", login);
  bindClick("openRegisterBtn", () => openModal("registerModal"));
  bindClick("regSubmitBtn", registerUser);
  bindClick("regCloseBtn", () => closeModal("registerModal"));
  bindClick("regCancelBtn", () => closeModal("registerModal"));
  bindClick("logoutBtn", logout);
  bindClick("reloadDetailBtn", loadInterfaceDetail);
  bindClick("executeBtn", executeInterface);
  bindClick("loadConfigsBtn", () => loadConfigs(true).catch((e) => showOutput(e)));
  bindClick("loadHistoriesBtn", () => refreshRuntimeData(true).catch((e) => showOutput(e)));
  bindClick("loadRetryTasksBtn", () => loadRetryTasks(true).catch((e) => showOutput(e)));
  bindClick("publishConfigBtn", publishConfig);
  bindClick("singleRetryBtn", singleRetry);
  bindClick("bulkRetryBtn", bulkRetry);
  bindClick("fixActionBtn", focusConfigSection);
  bindClick("ignoreActionBtn", ignoreFailure);
  bindClick("pollToggleBtn", togglePolling);
  bindClick("globalSearchBtn", () => runGlobalSearch().catch((e) => showOutput(e)));

  bindClick("dashRefreshBtn", () => reloadDashboard(dashboardWindowHours).catch((e) => showOutput(e)));
  bindClick("dashIncidentRefreshBtn", () => loadIncidentSummary(dashboardWindowHours).catch((e) => showOutput(e)));

  bindClick("navDashboardBtn", () => {
    showPanel("dashboard");
    reloadDashboard(dashboardWindowHours).catch((e) => showOutput(e));
    loadIncidentSummary(dashboardWindowHours).catch((e) => showOutput(e));
  });
  bindClick("navInterfacesBtn", () => showPanel("interfaces"));
  bindInterfaceSubtabs();
  bindRetriesSubtabs();
  bindClick("navConfigsBtn", () => showPanel("configs"));
  bindClick("navExecutionsBtn", () => showPanel("executions"));
  bindClick("navHistorySearchBtn", () => showPanel("historySearch"));
  bindClick("navRetriesBtn", () => showPanel("retries"));
  bindClick("navDlqBtn", () => {
    showPanel("dlq");
    bindDlqSubtabs();
    loadDlqMessages().catch((e) => showOutput(e));
    loadDlqReplayRequests().catch((e) => showOutput(e));
  });
  bindClick("navSchedulesBtn", () => {
    showPanel("schedules");
    loadSchedules().catch((e) => showOutput(e));
  });
  bindClick("navAuditBtn", () => {
    showPanel("audit");
    triggerAuditSearch().catch((e) => showOutput(e));
  });
  bindClick("navSettingsBtn", () => showPanel("settings"));
  bindClick("navOutputBtn", () => {
    showPanel("output");
    loadApiLogs(0).catch((e) => showOutput(e));
  });

  bindClick("loadApiLogsBtn", () => loadApiLogs(0).catch((e) => showOutput(e)));
  bindClick("clearApiLogsBtn", () => {
    cachedApiLogs = [];
    pendingApiLogs = [];
    renderApiLogBox([]);
    showToast("화면 로그를 비웠습니다.", "success");
  });

  bindClick("loadDlqBtn", () => loadDlqMessages().catch((e) => showOutput(e)));
  bindClick("dlqCreateReplayBtn", () => dlqCreateReplay().catch((e) => showOutput(e)));
  bindClick("dlqPrevBtn", () => {
    dlqState.page = Math.max(0, (dlqState.page ?? 0) - 1);
    setValue("dlqFilterPage", dlqState.page);
    loadDlqMessages().catch((e) => showOutput(e));
  });
  bindClick("dlqNextBtn", () => {
    dlqState.page = (dlqState.page ?? 0) + 1;
    setValue("dlqFilterPage", dlqState.page);
    loadDlqMessages().catch((e) => showOutput(e));
  });

  $("dlqSelectAll")?.addEventListener("change", (e) => toggleDlqSelectAll(e.target.checked));

  bindClick("loadDlqReplaysBtn", () => {
    dlqReplayState.page = 0;
    loadDlqReplayRequests().catch((e) => showOutput(e));
  });
  bindClick("dlqReplayPrevBtn", () => {
    dlqReplayState.page = Math.max(0, (dlqReplayState.page ?? 0) - 1);
    loadDlqReplayRequests().catch((e) => showOutput(e));
  });
  bindClick("dlqReplayNextBtn", () => {
    dlqReplayState.page = (dlqReplayState.page ?? 0) + 1;
    loadDlqReplayRequests().catch((e) => showOutput(e));
  });

  $("dlqReplaySelectAll")?.addEventListener("change", (e) => toggleDlqReplaySelectAll(e.target.checked));
  $("dlqReplayStatusFilter")?.addEventListener("change", () => {
    dlqReplayState.page = 0;
    loadDlqReplayRequests().catch((e) => showOutput(e));
  });
  $("dlqReplayFrom")?.addEventListener("change", () => {
    dlqReplayState.page = 0;
    loadDlqReplayRequests().catch((e) => showOutput(e));
  });
  $("dlqReplayTo")?.addEventListener("change", () => {
    dlqReplayState.page = 0;
    loadDlqReplayRequests().catch((e) => showOutput(e));
  });

  bindClick("dlqReplayApproveBtn", () => dlqReplayApprove().catch((e) => showOutput(e)));
  bindClick("dlqReplayRejectBtn", () => dlqReplayReject().catch((e) => showOutput(e)));
  bindClick("dlqReplayExecuteBtn", () => dlqReplayExecute().catch((e) => showOutput(e)));
  bindClick("loadSchedulesBtn", () => loadSchedules().catch((e) => showOutput(e)));
  bindClick("createScheduleBtn", () => createSchedule().catch((e) => showOutput(e)));
  bindClick("searchAuditBtn", () => triggerAuditSearch().catch((e) => showOutput(e)));
  bindClick("auditPrevBtn", () => searchAuditLogs(Math.max(0, cachedAuditSearch.page - 1)).catch((e) => showOutput(e)));
  bindClick("auditNextBtn", () => searchAuditLogs(Math.min(cachedAuditSearch.totalPages - 1, cachedAuditSearch.page + 1)).catch((e) => showOutput(e)));
  bindClick("auditDetailCloseBtn", () => closeModal("auditDetailModal"));

  $("globalSearchInput")?.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      runGlobalSearch().catch((e) => showOutput(e));
    } else if (event.key === "Escape") {
      closeGlobalSearchResults();
    }
  });

  const debouncedGlobalSearch = debounce(() => {
    runGlobalSearch().catch((e) => showOutput(e));
  }, 300);

  $("globalSearchInput")?.addEventListener("input", () => {
    const q = $("globalSearchInput")?.value?.trim() || "";
    if (!q) {
      closeGlobalSearchResults();
      return;
    }
    debouncedGlobalSearch();
  });

  document.addEventListener("click", (event) => {
    const box = $("globalSearchResults");
    const wrap = $("globalSearchInput")?.closest(".global-search");
    if (!box || !wrap) return;
    const target = event.target;
    if (!(target instanceof Node)) return;
    if (!wrap.contains(target)) {
      closeGlobalSearchResults();
    }
  });

  $("dashWindowPicker")?.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const btn = target.closest(".window-btn");
    if (!btn) return;
    const hours = Number(btn.dataset.hours || "24");
    if (!Number.isFinite(hours)) return;
    dashboardWindowHours = hours;
    document.querySelectorAll("#dashWindowPicker .window-btn").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    reloadDashboard(dashboardWindowHours).catch((e) => showOutput(e));
    loadIncidentSummary(dashboardWindowHours).catch((e) => showOutput(e));
  });

  bindClick("historySearchBtn", () => triggerHistorySearch().catch((e) => showOutput(e)));
  bindClick("historySearchResetBtn", () => {
    ["hsInterfaceCode","hsExecutionIdContains","hsErrorCode","hsErrorMessageContains","hsFromAt","hsToAt","hsLatencyMin","hsLatencyMax"].forEach((id) => {
      if ($(id)) $(id).value = "";
    });
    ["hsStatus","hsTriggerType","hsProtocolType"].forEach((id) => {
      if ($(id)) $(id).value = "";
    });
    if ($("hsPageSize")) $("hsPageSize").value = "50";
    cachedHistorySearch.criteria = null;
    setText("hsResultMeta", "-");
    const tbody = $("hsResultTable")?.querySelector("tbody");
    if (tbody) tbody.innerHTML = "";
  });
  bindClick("hsPrevBtn", () => searchHistories(Math.max(0, cachedHistorySearch.page - 1)).catch((e) => showOutput(e)));
  bindClick("hsNextBtn", () => searchHistories(Math.min(cachedHistorySearch.totalPages - 1, cachedHistorySearch.page + 1)).catch((e) => showOutput(e)));

  [
    "hsExecutionIdContains",
    "hsInterfaceCode",
    "hsProtocolType",
    "hsStatus",
    "hsTriggerType",
    "hsErrorCode",
    "hsErrorMessageContains",
    "hsFromAt",
    "hsToAt",
    "hsLatencyMin",
    "hsLatencyMax",
    "hsPageSize",
  ].forEach((id) => {
    $(id)?.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        triggerHistorySearch().catch((e) => showOutput(e));
      }
    });
  });

  ["auditActor", "auditAction", "auditTargetType", "auditFrom", "auditTo"].forEach((id) => {
    $(id)?.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        triggerAuditSearch().catch((e) => showOutput(e));
      }
    });
  });

  bindClick("execDetailCloseBtn", () => closeModal("executionDetailModal"));
  bindClick("execDetailGoConfigsBtn", () => {
    closeModal("executionDetailModal");
    focusConfigSection();
  });
  bindClick("execDetailRetryBtn", async () => {
    const execId = $("execDetailRetryBtn")?.dataset?.executionId || "";
    if (!execId) {
      return;
    }
    try {
      const result = await retryWithStrategy(execId, readRetryStrategy());
      showOutput(result);
      showToast("재처리 완료", "success");
      await refreshRuntimeData(false);
      closeModal("executionDetailModal");
      showPanel("executions");
      if (result.newExecutionId) {
        await openExecutionDetail(result.newExecutionId);
      }
    } catch (error) {
      showOutput(error);
      showToast(`재처리 실패: ${getErrorMessage(error)}`, "error");
      await refreshRuntimeData(false);
      if (selectedExecutionId) {
        await openExecutionDetail(selectedExecutionId);
      }
    }
  });

  $("pollIntervalSec")?.addEventListener("change", (event) => {
    pollIntervalMs = Math.max(1, Math.min(5, Number(event.target.value || "3"))) * 1000;
    if (polling) {
      startPolling();
    }
    setPollingUi();
  });

  $("interfaceSelect")?.addEventListener("change", async (event) => {
    selectedInterfaceCode = event.target.value;
    selectedExecutionId = "";
    await loadInterfaceDetail();
  });

  ["failureTimeFilter", "failureStatusFilter", "failureTypeFilter"].forEach((id) => {
    $(id)?.addEventListener("change", () => {
      selectedExecutionId = "";
      renderHistories();
      renderFlow();
    });
  });

  $("selectAllFailures")?.addEventListener("change", (event) => {
    const checked = event.target.checked;
    document.querySelectorAll(".history-select").forEach((input) => {
      if (!input.disabled) {
        input.checked = checked;
      }
    });
  });
}

async function bootstrap() {
  accessToken = localStorage.getItem("interfacehub.jwt") || "";
  if ($("baseUrl") && !$("baseUrl").value) {
    $("baseUrl").value = window.location.origin;
  }
  syncDashboardFields();
  $("execIdempotencyKey").value = makeIdempotencyKey();
  setPollingUi();
  initializeHandlers();
  await checkConnection();
  if (accessToken) {
    // localStorage에 저장된 토큰이 서버에서 여전히 유효한지 검증
    const sessionValid = await validateStoredSession();
    if (sessionValid) {
      showDashboardView();

      // 임시 중지: 자동 대시보드 API 호출 차단
      //await reloadDashboard(dashboardWindowHours);
      //await loadIncidentSummary(dashboardWindowHours);
      //await loadInterfaceOptions();
    } else {
      accessToken = "";
      localStorage.removeItem("interfacehub.jwt");
      showLoginView();
    }
  } else {
    showLoginView();
  }

  // 임시 중지: 60초마다 자동 대시보드 조회 차단
  // setInterval(() => {
  //   if (!accessToken) return;
  //   loadDashboardSummary(dashboardWindowHours).catch((e) => showOutput(e));
  // }, 60_000);
}

bootstrap().catch((error) => {
  showOutput(error);
  showToast(`초기화 실패: ${getErrorMessage(error)}`, "error");
});
