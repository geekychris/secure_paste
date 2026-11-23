// The index page's script. Kept out of the HTML so the
// Content-Security-Policy can forbid inline script (SecurityConfig).
'use strict';

const API_BASE = '/api/pastes';
let APP_CONFIG = null;

// Initialize application configuration
async function initializeApp() {
    try {
        const response = await fetch('/api/config');
        if (response.ok) {
            APP_CONFIG = await response.json();
            console.log('App config loaded:', APP_CONFIG);
        } else {
            console.warn('Failed to load app config, using fallback');
            APP_CONFIG = { baseUrl: window.location.origin };
        }
    } catch (error) {
        console.error('Error loading app config:', error);
        APP_CONFIG = { baseUrl: window.location.origin };
    }
}

// Get the configured base URL
function getBaseUrl() {
    return APP_CONFIG ? APP_CONFIG.baseUrl : window.location.origin;
}

// Show alert message
function showAlert(message, type = 'success') {
    const alert = document.getElementById('alert');
    alert.className = `alert alert-${type}`;
    alert.textContent = message;
    alert.style.display = 'block';
    setTimeout(() => {
        alert.style.display = 'none';
    }, 5000);
}

// Show/hide loading
function setLoading(show) {
    document.getElementById('loading').style.display = show ? 'block' : 'none';
}

// Show paste result section
function showPasteResult(result) {
    const pasteUrl = `${getBaseUrl()}/paste/${result.id}`;
    
    document.getElementById('pasteId').value = result.id;
    document.getElementById('pasteUrl').value = pasteUrl;
    document.getElementById('viewPasteLink').href = pasteUrl;

    const viewsNote = document.getElementById('viewsNote');
    if (result.maxViews) {
        viewsNote.textContent = result.maxViews === 1
            ? 'Burn after reading: this link can be revealed once, then the paste is deleted. Opening it shows a Reveal page first, so checking the link yourself uses nothing until you press Reveal.'
            : `This link can be revealed ${result.maxViews} times, then the paste is deleted. Opening it shows a Reveal page first; only Reveal uses a view.`;
        viewsNote.style.display = 'block';
    } else {
        viewsNote.style.display = 'none';
    }

    document.getElementById('resultSection').style.display = 'block';
    document.getElementById('resultSection').scrollIntoView({ behavior: 'smooth' });
}

// Copy text to clipboard
async function copyToClipboard(elementId) {
    const element = document.getElementById(elementId);
    const text = element.value;
    
    try {
        await navigator.clipboard.writeText(text);
        
        // Visual feedback
        const button = element.nextElementSibling;
        const originalText = button.textContent;
        button.textContent = 'Copied!';
        button.style.background = '#27ae60';
        
        setTimeout(() => {
            button.textContent = originalText;
            button.style.background = '';
        }, 2000);
    } catch (err) {
        // Fallback for older browsers
        element.select();
        document.execCommand('copy');
        showAlert('Copied to clipboard!', 'success');
    }
}

// Create another paste (hide result section)
function createAnotherPaste() {
    document.getElementById('resultSection').style.display = 'none';
    document.getElementById('title').focus();
}

// Handle form submission
document.getElementById('pasteForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const formData = new FormData(e.target);
    const data = {};
    
    // Convert form data to object
    for (let [key, value] of formData.entries()) {
        if (value.trim()) {
            if (key === 'expirationMinutes' || key === 'maxViews') {
                data[key] = parseInt(value);
            } else {
                data[key] = value;
            }
        }
    }

    try {
        setLoading(true);
        
        const response = await fetch(API_BASE, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(data)
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const result = await response.json();
        
        // Show persistent result section
        showPasteResult(result);
        
        // Clear form
        document.getElementById('pasteForm').reset();
        
        // Refresh recent pastes
        loadRecentPastes();

    } catch (error) {
        console.error('Error creating paste:', error);
        showAlert(`Error creating paste: ${error.message}`, 'error');
    } finally {
        setLoading(false);
    }
});

// Load recent public pastes
async function loadRecentPastes() {
    try {
        const response = await fetch(`${API_BASE}/public?size=5`);
        if (!response.ok) {
            throw new Error('Failed to fetch recent pastes');
        }
        
        const data = await response.json();
        const container = document.getElementById('recentPastes');

        if (data.content && data.content.length > 0) {
            // Built with textContent, never parsed as HTML: titles, authors and
            // languages are whatever anyone typed.
            container.replaceChildren(...data.content.map(paste => {
                const item = document.createElement('div');
                item.className = 'paste-item';
                const link = document.createElement('a');
                link.className = 'paste-title';
                link.href = `/paste/${encodeURIComponent(paste.id)}`;
                link.textContent = paste.title;
                const meta = document.createElement('div');
                meta.className = 'paste-meta';
                const parts = [];
                if (paste.language) parts.push(`Language: ${paste.language}`);
                parts.push(`Author: ${paste.authorName || 'Anonymous'}`);
                parts.push(`Views: ${paste.viewCount}`);
                parts.push(`Created: ${new Date(paste.createdAt).toLocaleString()}`);
                if (paste.expiresAt) parts.push(`Expires: ${new Date(paste.expiresAt).toLocaleString()}`);
                meta.textContent = parts.join(' | ');
                item.append(link, meta);
                return item;
            }));
        } else {
            setMessage(container, 'No public pastes found.');
        }
    } catch (error) {
        console.error('Error loading recent pastes:', error);
        setMessage(document.getElementById('recentPastes'), 'Error loading recent pastes.');
    }
}

function setMessage(container, text) {
    const p = document.createElement('p');
    p.textContent = text;
    container.replaceChildren(p);
}

// Buttons (no inline onclick: the CSP forbids inline script)
document.querySelectorAll('[data-copy]').forEach(button => {
    button.addEventListener('click', () => copyToClipboard(button.dataset.copy));
});
document.getElementById('createAnotherBtn').addEventListener('click', createAnotherPaste);

// Initialize app and load recent pastes on page load
document.addEventListener('DOMContentLoaded', async () => {
    await initializeApp();
    loadRecentPastes();
});
