// 브리지 v1 실기기 시험 페이지(T)의 공용 스크립트. index.html(최상위)과 frame.html(iframe)이 함께 쓴다.
// 외부 요청을 하지 않고, 화면 출력은 textContent로만 한다. 시험 전용이며 제품 코드가 아니다.
(function () {
  "use strict";

  var role = document.body.getAttribute("data-role") || "top";
  var LOG_KEY = "beone-bridge-test:log:" + role;
  var MAX_ENTRIES = 500;
  var LIMIT = 4096;

  function $(id) {
    return document.getElementById(id);
  }

  // 기록 보관: 같은 origin의 localStorage. 쓸 수 없으면 화면 기록만 유지한다.
  var memoryLog = [];
  var storageOk = true;

  // 쓰기가 한 번 실패하면 저장소의 이전 값 대신 메모리 기록을 기준으로 표시·추가한다.
  function loadLog() {
    if (!storageOk) return memoryLog;
    try {
      var raw = window.localStorage.getItem(LOG_KEY);
      var parsed = raw ? JSON.parse(raw) : [];
      memoryLog = Array.isArray(parsed) ? parsed : [];
      return memoryLog;
    } catch (e) {
      storageOk = false;
      return memoryLog;
    }
  }

  function saveLog(entries) {
    memoryLog = entries;
    try {
      window.localStorage.setItem(LOG_KEY, JSON.stringify(entries));
      storageOk = true;
    } catch (e) {
      storageOk = false;
    }
  }

  function now() {
    return new Date().toISOString();
  }

  function addEntry(kind, text, length) {
    var entries = loadLog();
    var entry = { t: now(), kind: kind, text: String(text) };
    if (typeof length === "number") entry.len = length;
    entries.push(entry);
    if (entries.length > MAX_ENTRIES) entries = entries.slice(entries.length - MAX_ENTRIES);
    saveLog(entries);
    renderLog();
  }

  var KIND_LABEL = { recv: "받음", sent: "보냄", note: "메모" };

  function renderLog() {
    var list = $("log");
    var entries = loadLog();
    list.textContent = "";
    for (var i = 0; i < entries.length; i++) {
      var e = entries[i];
      var li = document.createElement("li");
      li.className = "entry " + e.kind;
      var head = document.createElement("span");
      head.className = "head";
      head.textContent = e.t + " " + (KIND_LABEL[e.kind] || e.kind) + (typeof e.len === "number" ? " length=" + e.len : "");
      var body = document.createElement("code");
      body.textContent = e.text;
      li.appendChild(head);
      li.appendChild(body);
      list.appendChild(li);
    }
    $("log-count").textContent = entries.length + "건" + (storageOk ? "" : " (웹 저장소를 쓸 수 없어 새로고침하면 사라짐)");
  }

  // 브리지 존재 표시
  function bridgeType() {
    return typeof window.BeoneBridge;
  }

  function showBridgeType() {
    $("bridge-type").textContent = bridgeType();
  }

  // 받은 메시지 기록. 플랫폼이 넣은 객체의 message 이벤트를 쓰고, 없으면 onmessage를 쓴다.
  var listening = false;
  function attachListener() {
    if (listening || bridgeType() === "undefined" || window.BeoneBridge === null) return;
    var onMessage = function (event) {
      var data = event && typeof event === "object" && "data" in event ? event.data : event;
      addEntry("recv", typeof data === "string" ? data : "[문자열 아님] " + Object.prototype.toString.call(data));
    };
    if (typeof window.BeoneBridge.addEventListener === "function") {
      window.BeoneBridge.addEventListener("message", onMessage);
    } else {
      window.BeoneBridge.onmessage = onMessage;
    }
    listening = true;
  }

  // 입력 문자열을 가공하지 않고 그대로 보낸다.
  function send(value) {
    showBridgeType();
    if (bridgeType() === "undefined") {
      addEntry("note", "window.BeoneBridge 없음. 전송하지 않음", value.length);
      return;
    }
    attachListener();
    try {
      window.BeoneBridge.postMessage(value);
      addEntry("sent", value, value.length);
    } catch (e) {
      addEntry("note", "postMessage 예외: " + (e && e.name ? e.name : "오류") + " (보내려던 문자열은 다음 줄)", value.length);
      addEntry("note", value, value.length);
    }
  }

  // BR21: 문자열이 아닌 메시지(ArrayBuffer). WebView가 지원하지 않으면 예외를 기록한다.
  function sendArrayBuffer() {
    showBridgeType();
    if (bridgeType() === "undefined") {
      addEntry("note", "window.BeoneBridge 없음. ArrayBuffer 전송하지 않음");
      return;
    }
    attachListener();
    var text = '{"v":1,"type":"state.get"}';
    var buffer = new TextEncoder().encode(text).buffer;
    try {
      window.BeoneBridge.postMessage(buffer);
      addEntry("sent", "[ArrayBuffer " + buffer.byteLength + "바이트] UTF-8 " + text);
    } catch (e) {
      addEntry("note", "ArrayBuffer postMessage 예외: " + (e && e.name ? e.name : "오류") + " (관측 못 함으로 기록)");
    }
  }

  // 경계 문자열: {"v":V,"type":"state.get","pad":"…"} 의 pad를 채워 전체 length를 target으로 맞춘다.
  // fill: "ascii"(a), "hangul"(가, UTF-16 1), "emoji"(😀 하나 = UTF-16 2, 나머지는 a)
  function makePadded(v, fill, target) {
    var prefix = '{"v":' + v + ',"type":"state.get","pad":"';
    var suffix = '"}';
    var room = target - prefix.length - suffix.length;
    var pad;
    if (fill === "hangul") {
      pad = new Array(room + 1).join("가");
    } else if (fill === "emoji") {
      pad = "😀" + new Array(room - 2 + 1).join("a");
    } else {
      pad = new Array(room + 1).join("a");
    }
    return prefix + pad + suffix;
  }

  function setInput(value) {
    var input = $("message");
    input.value = value;
    updateLength();
  }

  function updateLength() {
    var value = $("message").value;
    var len = value.length;
    var out = $("message-length");
    out.textContent = len + (len > LIMIT ? " (상한 " + LIMIT + " 초과)" : "");
  }

  // 예시 버튼: data-preset 속성의 문자열을 그대로 입력란에 넣는다(전송은 [보내기]로 따로 한다).
  function bindPresets() {
    var buttons = document.querySelectorAll("[data-preset]");
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].addEventListener("click", function (event) {
        setInput(event.currentTarget.getAttribute("data-preset"));
      });
    }
    var pads = document.querySelectorAll("[data-pad]");
    for (var j = 0; j < pads.length; j++) {
      pads[j].addEventListener("click", function (event) {
        var parts = event.currentTarget.getAttribute("data-pad").split(":");
        setInput(makePadded(Number(parts[0]), parts[1], Number(parts[2])));
      });
    }
  }

  function bindControls() {
    $("message").addEventListener("input", updateLength);
    $("send").addEventListener("click", function () {
      send($("message").value);
    });
    $("send-buffer").addEventListener("click", sendArrayBuffer);
    $("refresh-type").addEventListener("click", function () {
      showBridgeType();
      addEntry("note", "typeof window.BeoneBridge = " + bridgeType());
    });
    $("clear-log").addEventListener("click", function () {
      saveLog([]);
      renderLog();
    });
  }

  // 최상위 페이지만: 주소·웹 기록 길이 표시, 다른 origin iframe 주소 계산과 iframe 로드 기록
  function setupTop() {
    var locationOut = $("location");
    if (locationOut) locationOut.textContent = window.location.href + " (history.length=" + window.history.length + ")";

    var visit = $("visit-link");
    if (visit) {
      var params = new URLSearchParams(window.location.search);
      var next = (Number(params.get("visit")) || 0) + 1;
      visit.setAttribute("href", "?visit=" + next);
      visit.textContent = "같은 페이지로 이동해 웹 기록 추가 (visit=" + next + ")";
    }

    var cross = $("cross-frame");
    var crossNote = $("cross-frame-note");
    if (cross) {
      // 다른 origin: debug 개발 서버(localhost)와 호스트가 다른 127.0.0.1의 두 번째 포트.
      // 평문 HTTP 예외는 debug 설정의 localhost·127.0.0.1·10.0.2.2뿐이므로 이 조합을 쓴다.
      var params2 = new URLSearchParams(window.location.search);
      var port = Number(params2.get("xport")) || 5174;
      if (window.location.hostname === "localhost" && port >= 1024 && port <= 65535) {
        var src = "http://127.0.0.1:" + port + "/frame.html";
        crossNote.textContent = "주소: " + src;
        cross.addEventListener("load", function () {
          addEntry("note", "다른 origin iframe load 이벤트 (" + src + "). 열렸는지는 iframe 화면으로 판정");
        });
        cross.setAttribute("src", src);
      } else {
        cross.parentNode.removeChild(cross);
        crossNote.textContent = "이 주소(" + window.location.origin + ")에서는 다른 origin iframe을 두지 않습니다. BR20은 debug(localhost)에서 확인합니다.";
      }
    }

    // load 기록을 놓치지 않도록 이벤트를 먼저 걸고 src를 넣는다.
    var same = $("same-frame");
    if (same) {
      same.addEventListener("load", function () {
        addEntry("note", "같은 origin iframe load 이벤트 (frame.html)");
      });
      same.setAttribute("src", "frame.html");
    }
  }

  function init() {
    $("origin").textContent = window.location.origin;
    showBridgeType();
    renderLog();
    bindPresets();
    bindControls();
    updateLength();
    if (role === "top") setupTop();
    attachListener();
    // 페이지가 새로 만들어졌는지 구분하는 기록(BR13 등). 메시지는 보내지 않는다.
    addEntry("note", "페이지 로드 " + window.location.href + " / typeof window.BeoneBridge = " + bridgeType());
  }

  init();
})();
