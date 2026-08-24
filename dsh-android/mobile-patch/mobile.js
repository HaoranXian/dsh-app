(function () {
  'use strict';

  // 1) AbortSignal.timeout polyfill（部分 WebView/Chrome<=102 缺失，会导致前端无限重连）
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
  if (viewport) {
    function onViewportChange() {
      var doc = document.documentElement;
      doc.style.height = viewport.height + 'px';
    }
    viewport.addEventListener('resize', onViewportChange);
    viewport.addEventListener('scroll', onViewportChange);
    onViewportChange();
  }

  // 3) 定位官方 AppFrame 并打上 [data-mobile-nav="frame"] 标记
  function findFrame() {
    return document.querySelector('[data-mobile-nav="frame"]')
      || (function () {
        var ov = document.querySelector('[data-shell-overlay]');
        return ov ? ov.parentElement : null;
      })()
      || document.querySelector('[data-sidebar-collapsed]')
      || null;
  }
  function ensureFrame() {
    var f = findFrame();
    if (f && !f.hasAttribute('data-mobile-nav')) {
      f.setAttribute('data-mobile-nav', 'frame');
    }
    return f;
  }

  // 4) 浮动按钮：窄屏显示，点击开合抽屉（切换 data-sidebar-collapsed）
  function ensureFab() {
    if (document.querySelector('[data-mobile-nav="fab"]')) return;
    var b = document.createElement('button');
    b.type = 'button';
    b.setAttribute('data-mobile-nav', 'fab');
    b.setAttribute('aria-label', '菜单');
    b.setAttribute('title', '目录');
    b.textContent = '☰';
    b.style.cssText = 'border:none;cursor:pointer;-webkit-tap-highlight-color:transparent;';
    b.addEventListener('click', function () {
      var f = findFrame();
      if (!f) return;
      if (f.hasAttribute('data-sidebar-collapsed')) {
        f.removeAttribute('data-sidebar-collapsed');
      } else {
        f.setAttribute('data-sidebar-collapsed', '');
      }
    });
    document.body.appendChild(b);
  }

  function install() {
    ensureFrame();
    ensureFab();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
  // SPA 路由/重挂载后再补一次
  if (window.MutationObserver) {
    var mo = new MutationObserver(function () {
      install();
    });
    mo.observe(document.documentElement, { childList: true, subtree: true });
  }
})();
