// The reveal page's Copy button. Kept out of the HTML so the
// Content-Security-Policy can forbid inline script (SecurityConfig).
'use strict';

const copyBtn = document.getElementById('copyBtn');
if (copyBtn) {
    copyBtn.addEventListener('click', async () => {
        const text = document.getElementById('pasteContent').textContent;
        const btn = document.getElementById('copyBtn');
        try {
            await navigator.clipboard.writeText(text);
        } catch (e) {
            const ta = document.createElement('textarea');
            ta.value = text;
            document.body.appendChild(ta);
            ta.select();
            document.execCommand('copy');
            document.body.removeChild(ta);
        }
        btn.textContent = 'Copied!';
        setTimeout(() => { btn.textContent = 'Copy Content'; }, 2000);
    });
}
