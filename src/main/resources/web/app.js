const STORAGE_KEY = "zenith-web-api-key";
const RECONNECT_DELAY_MS = 1000;

const state = {
    apiKey: "",
    isUnlocked: false,
    nextLogIndex: 0,
    socket: null,
    reconnectHandle: null,
    intentionalClose: false,
};

const ANSI_COLOR_MAP = {
    30: "#1b2433",
    31: "#e06c75",
    32: "#7bc379",
    33: "#d7ba7d",
    34: "#61afef",
    35: "#c678dd",
    36: "#56b6c2",
    37: "#d9e3f0",
    90: "#5c677d",
    91: "#ff7b72",
    92: "#8ddb8c",
    93: "#f2cc60",
    94: "#7cc7ff",
    95: "#d2a8ff",
    96: "#7ee7f2",
    97: "#ffffff",
};

const ANSI_BACKGROUND_MAP = {
    40: "#1b2433",
    41: "#742f39",
    42: "#24442d",
    43: "#5c4b1e",
    44: "#233b63",
    45: "#55325f",
    46: "#21474d",
    47: "#d9e3f0",
    100: "#5c677d",
    101: "#b24a52",
    102: "#2f6b3b",
    103: "#8a6d27",
    104: "#3f67a8",
    105: "#7b4f91",
    106: "#31727b",
    107: "#ffffff",
};

const elements = {
    authView: document.querySelector("#auth-view"),
    consoleView: document.querySelector("#console-view"),
    apiKey: document.querySelector("#api-key"),
    rememberKey: document.querySelector("#remember-key"),
    unlockButton: document.querySelector("#unlock-button"),
    forgetButton: document.querySelector("#forget-button"),
    authMessage: document.querySelector("#auth-message"),
    consoleStatus: document.querySelector("#console-status"),
    consoleOutput: document.querySelector("#console-output"),
    consoleMeta: document.querySelector("#console-meta"),
    commandForm: document.querySelector("#command-form"),
    commandInput: document.querySelector("#command-input"),
    sendCommandButton: document.querySelector("#send-command-button"),
};

function setUnlocked(unlocked, message) {
    state.isUnlocked = unlocked;
    elements.authView.classList.toggle("hidden", unlocked);
    elements.consoleView.classList.toggle("hidden", !unlocked);
    elements.commandInput.disabled = !unlocked;
    elements.sendCommandButton.disabled = !unlocked;
    elements.authMessage.textContent = message;
    elements.authMessage.classList.toggle("error", !unlocked && Boolean(message));
    setConsoleState(unlocked ? "busy" : "disconnected", unlocked ? "Connecting..." : "Waiting for authentication");
}

function setConsoleMeta(text) {
    elements.consoleMeta.textContent = text;
}

function setConsoleState(state, text) {
    elements.consoleStatus.classList.toggle("ok", state === "connected");
    elements.consoleStatus.classList.toggle("busy", state === "busy");
    setConsoleMeta(text);
}

function formatDroppedMessage(baseIndex, previousIndex) {
    const droppedCount = baseIndex - previousIndex;
    return `[older log entries dropped: ${droppedCount}]\n`;
}

function escapeHtml(text) {
    return text
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function styleToCss(style) {
    const css = [];
    if (style.foreground) {
        css.push(`color: ${style.foreground}`);
    }
    if (style.background) {
        css.push(`background-color: ${style.background}`);
    }
    return css.join("; ");
}

function applyAnsiCode(style, code) {
    if (code === 0) {
        style.bold = false;
        style.foreground = null;
        style.background = null;
        return;
    }
    if (code === 1) {
        style.bold = true;
        return;
    }
    if (code === 22) {
        style.bold = false;
        return;
    }
    if (code === 39) {
        style.foreground = null;
        return;
    }
    if (code === 49) {
        style.background = null;
        return;
    }
    if (ANSI_COLOR_MAP[code]) {
        style.foreground = ANSI_COLOR_MAP[code];
        return;
    }
    if (ANSI_BACKGROUND_MAP[code]) {
        style.background = ANSI_BACKGROUND_MAP[code];
    }
}

function renderAnsiToHtml(text) {
    const ansiRegex = /\u001b\[([0-9;]*)m/g;
    const style = { bold: false, foreground: null, background: null };
    let html = "";
    let lastIndex = 0;
    let match;

    while ((match = ansiRegex.exec(text)) !== null) {
        const chunk = text.slice(lastIndex, match.index);
        if (chunk) {
            const classes = style.bold ? ' class="ansi-bold"' : "";
            const inlineStyle = styleToCss(style);
            const styleAttr = inlineStyle ? ` style="${inlineStyle}"` : "";
            html += `<span${classes}${styleAttr}>${escapeHtml(chunk)}</span>`;
        }
        const codes = match[1] === "" ? [0] : match[1].split(";").map(value => Number.parseInt(value, 10) || 0);
        for (const code of codes) {
            applyAnsiCode(style, code);
        }
        lastIndex = ansiRegex.lastIndex;
    }

    const remainder = text.slice(lastIndex);
    if (remainder) {
        const classes = style.bold ? ' class="ansi-bold"' : "";
        const inlineStyle = styleToCss(style);
        const styleAttr = inlineStyle ? ` style="${inlineStyle}"` : "";
        html += `<span${classes}${styleAttr}>${escapeHtml(remainder)}</span>`;
    }

    return html;
}

function appendConsole(text) {
    if (!text) {
        return;
    }
    const shouldStickToBottom = Math.abs(
        elements.consoleOutput.scrollHeight -
        elements.consoleOutput.scrollTop -
        elements.consoleOutput.clientHeight
    ) < 16;
    elements.consoleOutput.insertAdjacentHTML("beforeend", renderAnsiToHtml(text));
    if (shouldStickToBottom) {
        elements.consoleOutput.scrollTop = elements.consoleOutput.scrollHeight;
    }
}

function replaceConsole(text) {
    elements.consoleOutput.innerHTML = renderAnsiToHtml(text);
    elements.consoleOutput.scrollTop = elements.consoleOutput.scrollHeight;
}

function syncConsoleFromPayload(payload) {
    state.nextLogIndex = payload.nextIndex;
    const prefix = payload.baseIndex > 0 ? formatDroppedMessage(payload.baseIndex, 0) : "";
    replaceConsole(payload.lines.length > 0 ? `${prefix}${payload.lines.join("")}` : "Connected\n");
    setConsoleState("connected", "Connected");
}

function appendLogsFromPayload(payload) {
    if (state.nextLogIndex < payload.baseIndex) {
        appendConsole(formatDroppedMessage(payload.baseIndex, state.nextLogIndex));
    }
    if (payload.lines.length > 0) {
        appendConsole(payload.lines.join(""));
    }
    state.nextLogIndex = payload.nextIndex;
}

function websocketUrl() {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const url = new URL("/api/ws", `${protocol}//${window.location.host}`);
    url.searchParams.set("token", state.apiKey);
    return url;
}

function closeSocket() {
    if (state.reconnectHandle !== null) {
        window.clearTimeout(state.reconnectHandle);
        state.reconnectHandle = null;
    }
    if (state.socket !== null) {
        state.intentionalClose = true;
        state.socket.close();
        state.socket = null;
    }
}

function scheduleReconnect() {
    if (state.reconnectHandle !== null || state.intentionalClose) {
        return;
    }
    state.reconnectHandle = window.setTimeout(() => {
        state.reconnectHandle = null;
        connectWebSocket(true);
    }, RECONNECT_DELAY_MS);
}

function handleServerMessage(event, reconnecting) {
    let payload;
    try {
        payload = JSON.parse(event.data);
    } catch {
        appendConsole("[connection error] Server sent an invalid message\n");
        return;
    }

    if (payload.type === "snapshot") {
        syncConsoleFromPayload(payload);
        setUnlocked(true, "Auth token accepted. Console unlocked.");
        setConsoleState("connected", reconnecting ? "Reconnected" : "Connected");
        elements.commandInput.focus();
        if (elements.rememberKey.checked) {
            window.localStorage.setItem(STORAGE_KEY, state.apiKey);
        } else {
            window.localStorage.removeItem(STORAGE_KEY);
        }
        return;
    }
    if (payload.type === "logs") {
        appendLogsFromPayload(payload);
        return;
    }
    if (payload.type === "error") {
        appendConsole(`[command failed] ${payload.reason}\n`);
    }
}

function connectWebSocket(reconnecting = false) {
    state.intentionalClose = false;
    const socket = new WebSocket(websocketUrl());
    state.socket = socket;

    socket.addEventListener("message", event => {
        if (state.socket === socket) {
            handleServerMessage(event, reconnecting);
        }
    });

    socket.addEventListener("close", () => {
        if (state.socket !== socket) {
            return;
        }
        state.socket = null;
        if (state.intentionalClose) {
            return;
        }
        if (!state.isUnlocked) {
            state.apiKey = "";
            replaceConsole("Enter a valid auth token to unlock.");
            setUnlocked(false, "Connection rejected. Check the auth token and server availability.");
            return;
        }
        setConsoleState("busy", "Connection lost. Retrying...");
        scheduleReconnect();
    });

    socket.addEventListener("error", () => {
        if (state.socket === socket && state.isUnlocked) {
            setConsoleState("busy", "Connection error. Retrying...");
        }
    });
}

function unlock() {
    const apiKey = elements.apiKey.value.trim();
    if (!apiKey) {
        setUnlocked(false, "Enter an auth token.");
        return;
    }

    state.apiKey = apiKey;
    state.nextLogIndex = 0;
    closeSocket();
    state.intentionalClose = false;
    replaceConsole("Connecting...\n");
    elements.authMessage.textContent = "Connecting...";
    elements.authMessage.classList.remove("error");
    connectWebSocket();
}

function forgetKey() {
    closeSocket();
    state.apiKey = "";
    state.nextLogIndex = 0;
    elements.apiKey.value = "";
    elements.rememberKey.checked = false;
    window.localStorage.removeItem(STORAGE_KEY);
    replaceConsole("Enter a valid API key to unlock access.");
    setUnlocked(false, "Stored API key cleared.");
}

function sendCommand(event) {
    event.preventDefault();
    if (!state.isUnlocked || state.socket?.readyState !== WebSocket.OPEN) {
        return;
    }

    const command = elements.commandInput.value.trim();
    if (!command) {
        return;
    }

    elements.sendCommandButton.disabled = true;
    const requestId = window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`;
    state.socket.send(JSON.stringify({ type: "command", requestId, command }));
    elements.commandInput.value = "";
    elements.sendCommandButton.disabled = false;
}

function loadStoredKey() {
    const storedKey = window.localStorage.getItem(STORAGE_KEY);
    if (!storedKey) {
        return;
    }
    elements.apiKey.value = storedKey;
    elements.rememberKey.checked = true;
}

elements.unlockButton.addEventListener("click", unlock);
elements.forgetButton.addEventListener("click", forgetKey);
elements.commandForm.addEventListener("submit", sendCommand);
elements.apiKey.addEventListener("keydown", event => {
    if (event.key === "Enter") {
        unlock();
    }
});

loadStoredKey();
setUnlocked(false, "Enter Auth Token");
