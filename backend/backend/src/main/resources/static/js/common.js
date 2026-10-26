// 모든 화면 공통: 서버 오류(5xx)·로그인 만료(401)를 화면 위쪽 안내 띠로 알려줌
(function () {
    const originalFetch = window.fetch;
    let bar = null;
    let hideTimer = null;

    function showBanner(text, linkText, linkHref) {
        if (!bar) {
            bar = document.createElement('div');
            bar.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:2000;padding:12px 16px;' +
                'background:#fff3e0;color:#e65100;border-bottom:2px solid #ffb74d;font-size:0.88rem;' +
                'text-align:center;cursor:pointer;';
            bar.onclick = function () { bar.style.display = 'none'; };
            document.body.appendChild(bar);
        }
        bar.textContent = text + ' ';
        if (linkText) {
            const a = document.createElement('a');
            a.href = linkHref;
            a.textContent = linkText;
            a.style.cssText = 'color:#e65100;font-weight:700;text-decoration:underline;';
            a.onclick = function (e) { e.stopPropagation(); };
            bar.appendChild(a);
        }
        bar.style.display = 'block';
        clearTimeout(hideTimer);
        hideTimer = setTimeout(function () { bar.style.display = 'none'; }, 10000);
    }

    window.fetch = async function (input, init) {
        const res = await originalFetch.call(window, input, init);
        const url = (typeof input === 'string') ? input : ((input && input.url) || '');

        // 퀴즈 생성은 화면이 직접 안내하니까 띠는 띄우지 않음
        if (url.indexOf('/quiz/generate') === 0) return res;

        if (res.status === 401) {
            showBanner('로그인이 필요하거나 로그인 유지 시간이 끝났어요.', '로그인하러 가기', '/login');
        } else if (res.status >= 500) {
            res.clone().json()
                .then(function (d) {
                    showBanner(d.message || '서버에서 오류가 발생했어요. 잠시 후 다시 시도해주세요.');
                })
                .catch(function () {
                    showBanner('서버에서 오류가 발생했어요. 잠시 후 다시 시도해주세요.');
                });
        }
        return res;
    };
})();