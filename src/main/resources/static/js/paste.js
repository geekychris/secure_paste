// The paste page's script. Kept out of the HTML so the
// Content-Security-Policy can forbid inline script (SecurityConfig).
'use strict';

const API_BASE = '/api/pastes';
const pasteId = window.location.pathname.split('/').pop();
let currentPaste = null;

// Show error message
function showError(message) {
    const errorDiv = document.getElementById('error');
    errorDiv.textContent = message;
    errorDiv.style.display = 'block';
    document.getElementById('loading').style.display = 'none';
}

// Load paste data
async function loadPaste(password = null) {
    try {
        document.getElementById('loading').style.display = 'block';
        document.getElementById('error').style.display = 'none';

        let url = `${API_BASE}/${pasteId}`;
        if (password) {
            url += `?password=${encodeURIComponent(password)}`;
        }

        const response = await fetch(url);
        
        if (response.status === 403) {
            // Password required
            document.getElementById('loading').style.display = 'none';
            document.getElementById('passwordForm').style.display = 'block';
            return;
        }
        
        if (!response.ok) {
            if (response.status === 404) {
                throw new Error('Paste not found: it may have expired, been deleted, or already been viewed the maximum number of times');
            }
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const paste = await response.json();
        currentPaste = paste;
        displayPaste(paste);

    } catch (error) {
        console.error('Error loading paste:', error);
        showError(error.message);
    }
}

// Display paste content
function displayPaste(paste) {
    document.getElementById('loading').style.display = 'none';
    document.getElementById('passwordForm').style.display = 'none';
    document.getElementById('pasteContainer').style.display = 'block';

    // Update title
    document.getElementById('pasteTitle').textContent = paste.title;
    document.title = `${paste.title} - SecurePaste`;

    // Update meta info
    const info = [];
    if (paste.language) info.push(`Language: ${paste.language}`);
    info.push(`Author: ${paste.authorName || 'Anonymous'}`);
    info.push(`Views: ${paste.viewCount}`);
    info.push(`Created: ${new Date(paste.createdAt).toLocaleString()}`);
    if (paste.expiresAt) info.push(`Expires: ${new Date(paste.expiresAt).toLocaleString()}`);
    info.push(`Visibility: ${paste.visibility}`);
    if (paste.passwordProtected) info.push('🔒 Password Protected');

    // textContent, never parsed as HTML: language and author are user input.
    document.getElementById('pasteInfo').replaceChildren(...info.map(item => {
        const span = document.createElement('span');
        span.textContent = item;
        return span;
    }));

    // Update content
    document.getElementById('pasteContent').textContent = paste.content;

    // Update raw link
    document.getElementById('rawLink').href = `${API_BASE}/${encodeURIComponent(paste.id)}`;
}

// Load paste with password
function loadPasteWithPassword() {
    const password = document.getElementById('password').value;
    if (password) {
        loadPaste(password);
    }
}

// Copy content to clipboard
async function copyToClipboard() {
    if (currentPaste) {
        try {
            await navigator.clipboard.writeText(currentPaste.content);
            alert('Content copied to clipboard!');
        } catch (err) {
            // Fallback for older browsers
            const textArea = document.createElement('textarea');
            textArea.value = currentPaste.content;
            document.body.appendChild(textArea);
            textArea.select();
            document.execCommand('copy');
            document.body.removeChild(textArea);
            alert('Content copied to clipboard!');
        }
    }
}

// Copy URL to clipboard
async function copyUrl() {
    try {
        await navigator.clipboard.writeText(window.location.href);
        alert('URL copied to clipboard!');
    } catch (err) {
        // Fallback
        const textArea = document.createElement('textarea');
        textArea.value = window.location.href;
        document.body.appendChild(textArea);
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
        alert('URL copied to clipboard!');
    }
}

// Buttons (no inline onclick: the CSP forbids inline script)
document.getElementById('passwordSubmitBtn').addEventListener('click', loadPasteWithPassword);
document.getElementById('copyContentBtn').addEventListener('click', copyToClipboard);
document.getElementById('copyUrlBtn').addEventListener('click', copyUrl);

// Handle Enter key in password field
document.getElementById('password').addEventListener('keypress', function(e) {
    if (e.key === 'Enter') {
        loadPasteWithPassword();
    }
});

// Load paste on page load
document.addEventListener('DOMContentLoaded', () => {
    loadPaste();
});
