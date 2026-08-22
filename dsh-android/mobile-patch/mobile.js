(function () {
  'use strict';

  // 1) AbortSignal.timeout polyfill
  // 部分厂商 WebView / Chrome <= 102 没有该 API；
  // 缺失会导致 dsh 前端 client-connection 无限重连（Vengisk 实测坑）。
  if (typeof AbortSignal !== 'undefined' && typeof AbortSignal.timeout !== 'function') {
    AbortSignal.timeout = function (ms) {
      var ctrl = new AbortController();
      setTimeout(function () {
        if (!ctrl.signal.aborted) {
          ctrl.abort(new DOMException('TimeoutError', 'TimeoutError'));
        }
      }, ms);
      return ctrl.signal;
    };
  }

  // 2) 软键盘：VisualViewport 方案，避免输入框被键盘顶掉
  var viewport = window.visualViewport;
  function onViewportChange() {
    if (!viewport) return;
    var doc = document.documentElement;
    doc.style.height = viewport.height + 'px';
  }
  if (viewport) {
    viewport.addEventListener('resize', onViewportChange);
    viewport.addEventListener('scroll', onViewportChange);
    onViewportChange();
  }

  // 3) 安全区兜底变量（具体布局由 CSS 消费）
  document.documentElement.style.setProperty('--dsh-safe-top', '12px');
  document.documentElement.style.setProperty('--dsh-safe-bottom', '12px');
})();
