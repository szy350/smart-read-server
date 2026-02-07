// HTML转义函数（需要在showToast之前定义）
function escapeHtml(str) {
    return String(str || '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}

/** 与后端 TencentOcrTextFormatter 的【注释】标记一致，将整页文本拆成正文与注释两段 */
function splitBodyAndNotes(fullText) {
    if (typeof fullText !== 'string') return { body: '', notes: '' };
    const marker = '【注释】';
    const idx = fullText.indexOf(marker);
    if (idx === -1) return { body: fullText.trim(), notes: '' };
    const body = fullText.substring(0, idx).trim();
    let notes = fullText.substring(idx + marker.length).trim();
    notes = notes.replace(/^\n+/, '');
    return { body, notes };
}

// Toast 提示组件
function showToast(message, type = 'info', duration = 3000) {
    const container = document.getElementById('toastContainer');
    if (!container) return;
    
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    
    const icons = {
        success: '✓',
        error: '✕',
        warning: '⚠',
        info: 'ℹ'
    };
    
    toast.innerHTML = `
        <span class="toast-icon">${icons[type] || icons.info}</span>
        <div class="toast-content">
            <div class="toast-message">${escapeHtml(message)}</div>
        </div>
        <button class="toast-close" aria-label="关闭">×</button>
    `;
    
    container.appendChild(toast);
    
    // 关闭按钮
    const closeBtn = toast.querySelector('.toast-close');
    const closeToast = () => {
        toast.classList.add('toast-exiting');
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 300);
    };
    
    closeBtn.addEventListener('click', closeToast);
    
    // 自动关闭
    if (duration > 0) {
        setTimeout(closeToast, duration);
    }
    
    return toast;
}

// 流式显示文本
function typeWriter(element, text, speed = 20) {
    if (!element) return Promise.resolve();
    
    return new Promise((resolve) => {
        let index = 0;
        element.textContent = '';
        
        function type() {
            if (index < text.length) {
                const char = text[index];
                element.textContent += char;
                index++;
                
                // 自动滚动到底部
                const container = element.closest('.ai-qa-list');
                if (container) {
                    container.scrollTop = container.scrollHeight;
                }
                
                setTimeout(type, speed);
            } else {
                resolve();
            }
        }
        
        type();
    });
}

// 登录和注册弹窗功能
document.addEventListener('DOMContentLoaded', function() {
    // API配置
    // 使用同源地址，避免部署到服务器后仍请求客户端本机 127.0.0.1
    const API_BASE_URL = window.location.origin;
    const loginBtn = document.querySelector('.login-btn');
    const loginModal = document.getElementById('loginModal');
    const registerModal = document.getElementById('registerModal');
    const closeLoginBtn = document.getElementById('closeLoginModal');
    const closeRegisterBtn = document.getElementById('closeRegisterModal');
    const loginForm = document.querySelector('.login-form');
    const registerForm = document.querySelector('.register-form');
    const aiReadingTab = document.getElementById('aiReadingTab');
    const bookshelfTab = document.getElementById('bookshelfTab');
    const homeTab = document.getElementById('homeTab');
    const articleSection = document.getElementById('articleSection');
    const aiReadingSection = document.getElementById('aiReadingSection');
    const bookshelfSection = document.getElementById('bookshelfSection');
    const aiUploadForm = document.getElementById('aiReadingUploadForm');
    const aiUploadMessage = document.getElementById('aiUploadMessage');
    const aiReadingFileInput = document.getElementById('aiReadingFile');
    // Bookshelf elements
    const bookshelfContainer = document.getElementById('bookshelfContainer');
    const bookshelfList = document.getElementById('bookshelfList');
    const bookshelfEmpty = document.getElementById('bookshelfEmpty');
    const refreshBookshelfBtn = document.getElementById('refreshBookshelfBtn');
    const bookshelfSearchInput = document.getElementById('bookshelfSearchInput');
    const bookshelfPagination = document.getElementById('bookshelfPagination');
    const bookshelfPrevBtn = document.getElementById('bookshelfPrevBtn');
    const bookshelfNextBtn = document.getElementById('bookshelfNextBtn');
    const bookshelfPageNumbers = document.getElementById('bookshelfPageNumbers');

    // Bookshelf workspace elements (4-column layout)
    const bookshelfNotesStatus = document.getElementById('bookshelfNotesStatus');
    const bookshelfNotesInput = document.getElementById('bookshelfNotesInput');
    const notesSelect = document.getElementById('notesSelect');
    const newNoteBtn = document.getElementById('newNoteBtn');
    const stashNoteBtn = document.getElementById('stashNoteBtn');
    const exportNoteBtn = document.getElementById('exportNoteBtn');
    const notesUpdateTime = document.getElementById('notesUpdateTime');
    const bookshelfReaderTitle = document.getElementById('bookshelfReaderTitle');
    const readerContent = document.getElementById('readerContent');
    const readerPageInfo = document.getElementById('readerPageInfo');
    const readerPrevPageBtn = document.getElementById('readerPrevPageBtn');
    const readerNextPageBtn = document.getElementById('readerNextPageBtn');
    const readerJumpInput = document.getElementById('readerJumpInput');
    const readerJumpBtn = document.getElementById('readerJumpBtn');
    const aiQaList = document.getElementById('aiQaList');
    const aiQuestionInput = document.getElementById('aiQuestionInput');
    const aiAskBtn = document.getElementById('aiAskBtn');
    const aiContextBar = document.getElementById('aiContextBar');
    const aiContextPreview = document.getElementById('aiContextPreview');
    const aiContextClearBtn = document.getElementById('aiContextClearBtn');
    const aiSelectionBar = document.getElementById('aiSelectionBar');
    const aiSelectionPreview = document.getElementById('aiSelectionPreview');
    const addAiSelectionToNotesBtn = document.getElementById('addAiSelectionToNotesBtn');
    const dismissAiSelectionBarBtn = document.getElementById('dismissAiSelectionBarBtn');

    // Reader modal elements
    const bookReaderModal = document.getElementById('bookReaderModal');
    const closeBookReaderModalBtn = document.getElementById('closeBookReaderModal');
    const bookReaderTitle = document.getElementById('bookReaderTitle');
    const bookReaderContent = document.getElementById('bookReaderContent');
    const bookReaderPageInfo = document.getElementById('bookReaderPageInfo');
    const bookPrevPageBtn = document.getElementById('bookPrevPageBtn');
    const bookNextPageBtn = document.getElementById('bookNextPageBtn');

    let currentBookPages = [];
    let currentBookPageIndex = 0;
    let currentBookName = '';
    let selectedBookId = null;
    let selectedBookFileName = '';
    let selectedBookDisplayName = '';
    let selectedBookTotalPages = 0;
    let notesSaveTimer = null;
    let notesServerSaveTimer = null;
    let currentNoteId = null;
    let currentNotesList = []; // [{id, content, updateTime, createTime, summary}]
    let draftNewNote = false;  // 新增笔记草稿：清空但不入库，直到点击“暂存”
    let aiSelectedContext = '';
    let aiSelectedContextPageNo = 0;
    let hisActionSaveTimer = null;
    let hisActionRestoreDone = false; // 仅用于避免重复恢复；不要在发请求前置 true
    let hisActionRestoreInFlight = false;
    let lastAiSelectionText = '';

    // bookshelf paging state
    let bookshelfAllBooks = [];
    let bookshelfFilteredBooks = [];
    let bookshelfCurrentPage = 1;
    const bookshelfPageSize = 12; // list paging
    let duplicateBlocked = false; // 是否因同名文件被禁止上传
    
    // Parsing progress elements
    const parsingProgressContainer = document.getElementById('parsingProgressContainer');
    // 注意：解析进度现在支持多条堆叠展示，因此不再使用单条的 fill/percentage/status/fileName DOM
    let parsingInterval = null;

    function setActiveTab(tabName) {
        if (homeTab) {
            homeTab.classList.toggle('active', tabName === 'home');
        }
        if (aiReadingTab) {
            aiReadingTab.classList.toggle('active', tabName === 'ai');
        }
        if (bookshelfTab) {
            bookshelfTab.classList.toggle('active', tabName === 'bookshelf');
        }
    }

    function switchToHome() {
        setActiveTab('home');
        document.body.classList.remove('bookshelf-mode');
        if (articleSection) {
            articleSection.classList.remove('hidden');
        }
        if (aiReadingSection) {
            aiReadingSection.classList.add('hidden');
        }
        if (bookshelfSection) {
            bookshelfSection.classList.add('hidden');
        }
    }

    function switchToAiReading() {
        if (!loginBtn.classList.contains('logged-in')) {
            showToast('请先登录以使用AI阅读功能', 'warning');
            return;
        }
        setActiveTab('ai');
        document.body.classList.remove('bookshelf-mode');
        if (articleSection) {
            articleSection.classList.add('hidden');
        }
        if (aiReadingSection) {
            aiReadingSection.classList.remove('hidden');
        }
        if (bookshelfSection) {
            bookshelfSection.classList.add('hidden');
        }
        
        // Check for processing files when entering the tab
        checkProcessingFile();

        // 书架放到“书架页签”，这里不再加载
    }

    function switchToBookshelf() {
        if (!loginBtn.classList.contains('logged-in')) {
            showToast('请先登录以查看书架', 'warning');
            return;
        }
        setActiveTab('bookshelf');
        document.body.classList.add('bookshelf-mode');
        if (articleSection) {
            articleSection.classList.add('hidden');
        }
        if (aiReadingSection) {
            aiReadingSection.classList.add('hidden');
        }
        if (bookshelfSection) {
            bookshelfSection.classList.remove('hidden');
        }

        const userName = loginBtn.textContent || localStorage.getItem('rememberedUser');
        if (userName && userName !== '登陆') {
            resetBookshelfWorkspace();
            // 每次进入书架都允许尝试一次恢复（如果之前请求失败/后端未更新，避免“永久不恢复”）
            hisActionRestoreDone = false;
            hisActionRestoreInFlight = false;
            applySavedBookshelfColumnWidths(userName);
            loadBookshelf(userName);
        }
    }

    // ========== 书架四列：可拖拽调整宽度（带限制 + 本地持久化） ==========
    const bookshelfLayoutEl = document.querySelector('.bookshelf-layout');
    const bookshelfResizers = bookshelfLayoutEl ? bookshelfLayoutEl.querySelectorAll('.col-resizer') : [];
    const BOOKSHELF_COL_KEY_PREFIX = 'bookshelf:cols:';

    function clamp(n, min, max) {
        return Math.max(min, Math.min(max, n));
    }

    // ========== 正文区域：拖拽列宽时自适应字号 ==========
    function calcReaderFontSizePx(widthPx) {
        // 经验值：正文列最小宽 420 左右时约 13px；宽到 700 左右时约 20px
        // 可按偏好微调：分母越大，整体字号越小
        const min = 13;
        const max = 22;
        const w = Number(widthPx) || 0;
        if (w <= 0) return 16;
        return clamp(w / 35, min, max);
    }

    function rafThrottle(fn) {
        let rafId = 0;
        let lastArgs = null;
        return (...args) => {
            lastArgs = args;
            if (rafId) return;
            rafId = requestAnimationFrame(() => {
                rafId = 0;
                fn(...(lastArgs || []));
            });
        };
    }

    function setReaderFontSizeVar(el, widthPx) {
        if (!el) return;
        // display:none 时宽度可能为 0，忽略即可
        if (!Number.isFinite(widthPx) || widthPx < 50) return;
        const size = calcReaderFontSizePx(widthPx);
        el.style.setProperty('--reader-font-size', `${size.toFixed(1)}px`);
    }

    function initAdaptiveReaderFontSize() {
        // 工作台正文列
        const readerContainer = readerContent; // #readerContent
        // 弹窗阅读器正文
        const modalReaderContainer = bookReaderContent; // #bookReaderContent

        if (typeof ResizeObserver === 'undefined') {
            // 兼容极老浏览器：至少在窗口 resize 时更新一次
            const onResize = () => {
                if (readerContainer) setReaderFontSizeVar(readerContainer, readerContainer.getBoundingClientRect().width);
                if (modalReaderContainer) setReaderFontSizeVar(modalReaderContainer, modalReaderContainer.getBoundingClientRect().width);
            };
            window.addEventListener('resize', rafThrottle(onResize));
            onResize();
            return;
        }

        const updateReader = rafThrottle((el) => {
            const rect = el.getBoundingClientRect();
            setReaderFontSizeVar(el, rect.width);
        });

        const ro = new ResizeObserver((entries) => {
            for (const entry of entries) {
                const target = entry && entry.target;
                if (!target) continue;
                // contentRect.width 在部分场景更稳定
                const w = entry.contentRect && entry.contentRect.width ? entry.contentRect.width : target.getBoundingClientRect().width;
                setReaderFontSizeVar(target, w);
            }
        });

        if (readerContainer) {
            ro.observe(readerContainer);
            updateReader(readerContainer);
        }
        if (modalReaderContainer) {
            ro.observe(modalReaderContainer);
            updateReader(modalReaderContainer);
        }
    }

    function getBookshelfLayoutConstraints(containerWidth) {
        // 给每列一个合理区间（可按你的审美继续微调）
        return {
            books: { min: 140, max: 260 },
            notes: { min: 200, max: 420 },
            reader: { min: 420, max: Math.max(520, containerWidth) },
            ai: { min: 240, max: 420 },
            resizer: 10
        };
    }

    function readCurrentColWidthsPx() {
        if (!bookshelfLayoutEl) return null;
        const cs = getComputedStyle(bookshelfLayoutEl);
        const toNum = (v) => parseFloat(String(v || '').replace('px', '')) || 0;
        return {
            books: toNum(cs.getPropertyValue('--col-books')),
            notes: toNum(cs.getPropertyValue('--col-notes')),
            reader: toNum(cs.getPropertyValue('--col-reader')),
            ai: toNum(cs.getPropertyValue('--col-ai'))
        };
    }

    function setColWidthsPx(widths) {
        if (!bookshelfLayoutEl || !widths) return;
        bookshelfLayoutEl.style.setProperty('--col-books', `${Math.round(widths.books)}px`);
        bookshelfLayoutEl.style.setProperty('--col-notes', `${Math.round(widths.notes)}px`);
        bookshelfLayoutEl.style.setProperty('--col-reader', `${Math.round(widths.reader)}px`);
        bookshelfLayoutEl.style.setProperty('--col-ai', `${Math.round(widths.ai)}px`);
    }

    function normalizeWidthsToContainer(widths, containerWidth) {
        const c = getBookshelfLayoutConstraints(containerWidth);
        const available = Math.max(0, containerWidth - c.resizer * 3);

        // 先 clamp 三个侧边列
        const books = clamp(widths.books, c.books.min, c.books.max);
        const notes = clamp(widths.notes, c.notes.min, c.notes.max);
        const ai = clamp(widths.ai, c.ai.min, c.ai.max);

        // reader 用剩余填充
        let reader = available - books - notes - ai;

        // 如果 reader 太小：按 notes -> ai -> books 的顺序回收空间
        let b = books, n = notes, a = ai;
        const need = () => Math.max(0, c.reader.min - reader);
        if (reader < c.reader.min) {
            let deficit = need();
            const take = (val, minVal) => {
                const can = Math.max(0, val - minVal);
                const t = Math.min(can, deficit);
                deficit -= t;
                return val - t;
            };
            n = take(n, c.notes.min);
            a = take(a, c.ai.min);
            b = take(b, c.books.min);
            reader = available - b - n - a;
        }

        // 仍然不足就硬 clamp（极窄窗口）
        reader = clamp(reader, Math.min(c.reader.min, available), c.reader.max);

        // 如果还有富余（例如窗口变宽），全部补到 reader
        const total = b + n + a + reader;
        const extra = available - total;
        if (extra > 0) reader += extra;

        return { books: b, notes: n, reader, ai: a };
    }

    function getStorageKeyForCols(userName) {
        return `${BOOKSHELF_COL_KEY_PREFIX}${String(userName || '')}`;
    }

    function saveBookshelfColumnWidths(userName, widths) {
        try {
            localStorage.setItem(getStorageKeyForCols(userName), JSON.stringify(widths));
        } catch (e) {
            // ignore
        }
    }

    function applySavedBookshelfColumnWidths(userName) {
        if (!bookshelfLayoutEl) return;
        const containerWidth = bookshelfLayoutEl.getBoundingClientRect().width || 0;
        const c = getBookshelfLayoutConstraints(containerWidth);
        bookshelfLayoutEl.style.setProperty('--col-resizer', `${c.resizer}px`);

        let widths = null;
        try {
            const raw = localStorage.getItem(getStorageKeyForCols(userName));
            if (raw) widths = JSON.parse(raw);
        } catch (e) {
            widths = null;
        }
        const current = readCurrentColWidthsPx() || { books: 180, notes: 240, reader: 700, ai: 300 };
        const merged = widths && typeof widths === 'object'
            ? { ...current, ...widths }
            : current;
        setColWidthsPx(normalizeWidthsToContainer(merged, containerWidth));
    }

    function initBookshelfResizers() {
        if (!bookshelfLayoutEl || !bookshelfResizers || bookshelfResizers.length === 0) return;
        let dragging = null;

        const beginDrag = (e, which) => {
            // 仅桌面/宽屏启用（窄屏会堆叠）
            if (window.matchMedia && window.matchMedia('(max-width: 900px)').matches) return;
            const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
            if (!userName || userName === '登陆') return;
            const rect = bookshelfLayoutEl.getBoundingClientRect();
            const containerWidth = rect.width || 0;
            const widths = normalizeWidthsToContainer(readCurrentColWidthsPx() || { books: 180, notes: 240, reader: 700, ai: 300 }, containerWidth);
            const c = getBookshelfLayoutConstraints(containerWidth);

            dragging = {
                which,
                startX: e.clientX,
                userName,
                containerWidth,
                widths,
                constraints: c
            };
            e.currentTarget.classList.add('dragging');
            try { e.currentTarget.setPointerCapture(e.pointerId); } catch (_) {}
            document.body.style.cursor = 'col-resize';
            document.body.style.userSelect = 'none';
        };

        const onMove = (e) => {
            if (!dragging) return;
            const dxRaw = e.clientX - dragging.startX;
            const w = { ...dragging.widths };
            const c = dragging.constraints;

            // which: books-notes / notes-reader / reader-ai
            const pair =
                dragging.which === 'books-notes' ? ['books', 'notes'] :
                dragging.which === 'notes-reader' ? ['notes', 'reader'] :
                ['reader', 'ai'];

            const left = pair[0];
            const right = pair[1];

            const leftMin = c[left].min, leftMax = c[left].max;
            const rightMin = c[right].min, rightMax = c[right].max;

            // dx 的允许范围
            const dxMin = Math.max(leftMin - w[left], w[right] - rightMax);
            const dxMax = Math.min(leftMax - w[left], w[right] - rightMin);
            const dx = clamp(dxRaw, dxMin, dxMax);

            w[left] = w[left] + dx;
            w[right] = w[right] - dx;

            setColWidthsPx(w);
        };

        const endDrag = () => {
            if (!dragging) return;
            const userName = dragging.userName;
            const w = readCurrentColWidthsPx();
            if (w) saveBookshelfColumnWidths(userName, w);
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
            dragging = null;
            bookshelfResizers.forEach((r) => r.classList.remove('dragging'));
        };

        bookshelfResizers.forEach((r) => {
            r.addEventListener('pointerdown', (e) => beginDrag(e, r.getAttribute('data-resizer')));
        });
        window.addEventListener('pointermove', onMove);
        window.addEventListener('pointerup', endDrag);
        window.addEventListener('pointercancel', endDrag);

        // 窗口变化时：重新归一化，避免溢出
        window.addEventListener('resize', () => {
            const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
            if (!userName || userName === '登陆') return;
            if (!document.body.classList.contains('bookshelf-mode')) return;
            applySavedBookshelfColumnWidths(userName);
        });
    }

    initBookshelfResizers();
    initAdaptiveReaderFontSize();

    function checkProcessingFile() {
        const userName = loginBtn.textContent || localStorage.getItem('rememberedUser');
        if (!userName || userName === '登陆') return;

        // Don't check if we are already polling to avoid duplicate polls
        if (parsingInterval) return;

        // 进入页面后，直接轮询“解析中列表”，实现多条任务堆叠展示
        startParsingListPolling(userName);
    }

    function showUploadMessage(message, isSuccess) {
        if (!aiUploadMessage) return;

        if (!message) {
            aiUploadMessage.textContent = '';
            aiUploadMessage.classList.add('hidden');
            aiUploadMessage.classList.remove('success', 'error');
            return;
        }

        aiUploadMessage.textContent = message;
        aiUploadMessage.classList.remove('hidden', 'success', 'error');
        aiUploadMessage.classList.add(isSuccess ? 'success' : 'error');
    }

    function setUploadButtonDisabled(disabled) {
        if (!aiUploadForm) return;
        const uploadBtn = aiUploadForm.querySelector('.ai-reading-upload-btn');
        if (uploadBtn) uploadBtn.disabled = !!disabled;
    }

    function checkDuplicateOnSelect(file, userName) {
        if (!file || !userName) return;

        duplicateBlocked = false;
        setUploadButtonDisabled(false);

        // 仅允许 txt/doc/docx/pdf（与提交时一致）
        const allowedExt = ['txt', 'doc', 'docx', 'pdf', 'png', 'jpg', 'jpeg'];
        const lowerName = (file.name || '').toLowerCase();
        const dotIndex = lowerName.lastIndexOf('.');
        const ext = dotIndex >= 0 ? lowerName.substring(dotIndex + 1) : '';
        if (!allowedExt.includes(ext)) return;

        fetch(`${API_BASE_URL}/ai-reading/check-duplicate?userName=${encodeURIComponent(userName)}&fileName=${encodeURIComponent(file.name || '')}`, {
            method: 'POST'
        })
        .then(res => res.json())
        .then(data => {
            if (data.code !== "0" && data.code !== "200") return;
            let payload = data.data || {};
            if (typeof payload === 'string') {
                try { payload = JSON.parse(payload); } catch (e) { payload = {}; }
            }
            const exists = !!(payload && payload.exists);
            if (exists) {
                duplicateBlocked = true;
                setUploadButtonDisabled(true);
                showUploadMessage('该文件名已存在，请修改文件名后再上传', false);
            } else {
                // 若之前提示过重复，这里清掉提示
                showUploadMessage('', true);
            }
        })
        .catch(() => {
            // 忽略预校验错误，不影响后端最终校验
        });
    }

    function showUploadProgress() {
        const uploadProgressContainer = document.getElementById('uploadProgressContainer');
        if (uploadProgressContainer) {
            uploadProgressContainer.classList.remove('hidden');
        }
    }

    function hideUploadProgress() {
        const uploadProgressContainer = document.getElementById('uploadProgressContainer');
        if (uploadProgressContainer) {
            uploadProgressContainer.classList.add('hidden');
        }
        resetUploadProgress();
    }

    function showParsingProgress() {
        if (parsingProgressContainer) {
            parsingProgressContainer.classList.remove('hidden');
        }
    }

    function hideParsingProgress() {
        if (parsingProgressContainer) {
            parsingProgressContainer.classList.add('hidden');
        }
        resetParsingProgress();
        if (parsingInterval) {
            clearInterval(parsingInterval);
            parsingInterval = null;
        }
    }

    function resetParsingProgress() {
        const summaryEl = document.getElementById('parsingProgressSummary');
        if (summaryEl) summaryEl.textContent = '0 个任务';
        const listEl = document.getElementById('parsingProgressList');
        if (listEl) listEl.innerHTML = '';
    }

    // escapeHtml 已在文件顶部定义，这里不再重复定义

    function truncateText(str, maxLen) {
        const s = String(str || '').trim();
        if (!s) return '';
        const n = Number(maxLen) || 160;
        if (s.length <= n) return s;
        return s.slice(0, n) + '...';
    }

    function showAiSelectionBar(text) {
        if (!aiSelectionBar || !aiSelectionPreview) return;
        const t = String(text || '').trim();
        if (!t) {
            aiSelectionBar.classList.add('hidden');
            aiSelectionPreview.textContent = '';
            return;
        }
        lastAiSelectionText = t;
        aiSelectionPreview.textContent = '已选中 AI 内容：' + truncateText(t, 160);
        aiSelectionBar.classList.remove('hidden');
    }

    function hideAiSelectionBar() {
        lastAiSelectionText = '';
        if (aiSelectionBar) aiSelectionBar.classList.add('hidden');
        if (aiSelectionPreview) aiSelectionPreview.textContent = '';
    }

    function setAiSelectedContext(text, pageNo) {
        aiSelectedContext = String(text || '').trim();
        aiSelectedContextPageNo = Number(pageNo) || 0;
        renderAiContextBar();
    }

    function clearAiSelectedContext() {
        aiSelectedContext = '';
        aiSelectedContextPageNo = 0;
        renderAiContextBar();
    }

    function renderAiContextBar() {
        if (!aiContextBar || !aiContextPreview) return;
        const has = !!(aiSelectedContext && aiSelectedContext.trim());
        aiContextBar.classList.toggle('hidden', !has);
        if (!has) {
            aiContextPreview.textContent = '';
            return;
        }
        const prefix = aiSelectedContextPageNo ? `第${aiSelectedContextPageNo}页选中：` : '选中：';
        aiContextPreview.textContent = prefix + truncateText(aiSelectedContext, 220);
    }

    // ========== 我的书架 & 阅读器 ==========
    function resetBookshelfWorkspace() {
        selectedBookId = null;
        selectedBookFileName = '';
        currentBookPages = [];
        currentBookPageIndex = 0;
        currentBookName = '';
        selectedBookTotalPages = 0;
        if (bookshelfReaderTitle) bookshelfReaderTitle.textContent = '正文';
        if (readerPageInfo) readerPageInfo.textContent = '第 0/0 页';
        if (readerContent) readerContent.innerHTML = `<div class="bookshelf-placeholder">请选择左侧一本书开始阅读</div>`;
        if (readerPrevPageBtn) readerPrevPageBtn.disabled = true;
        if (readerNextPageBtn) readerNextPageBtn.disabled = true;
        if (readerJumpInput) {
            readerJumpInput.value = '';
            readerJumpInput.disabled = true;
        }
        if (readerJumpBtn) readerJumpBtn.disabled = true;
        if (bookshelfNotesStatus) bookshelfNotesStatus.textContent = '未选择书籍';
        if (bookshelfNotesInput) {
            bookshelfNotesInput.value = '';
            bookshelfNotesInput.disabled = true;
        }
        if (notesUpdateTime) notesUpdateTime.textContent = '';
        if (notesSelect) {
            notesSelect.innerHTML = `<option value="">暂无笔记</option>`;
            notesSelect.disabled = true;
        }
        if (newNoteBtn) newNoteBtn.disabled = true;
        if (stashNoteBtn) stashNoteBtn.disabled = true;
        if (exportNoteBtn) exportNoteBtn.disabled = true;
        currentNoteId = null;
        currentNotesList = [];
        draftNewNote = false;
        if (aiQuestionInput) {
            aiQuestionInput.value = '';
            aiQuestionInput.disabled = true;
        }
        if (aiAskBtn) aiAskBtn.disabled = true;
        if (aiQaList) aiQaList.innerHTML = '';
        clearAiSelectedContext();
        hideAiSelectionBar();
    }

    function setWorkspaceEnabledForBook(enabled) {
        if (bookshelfNotesInput) bookshelfNotesInput.disabled = !enabled;
        if (notesSelect) notesSelect.disabled = !enabled;
        if (newNoteBtn) newNoteBtn.disabled = !enabled;
        if (stashNoteBtn) stashNoteBtn.disabled = !enabled;
        if (exportNoteBtn) exportNoteBtn.disabled = !enabled;
        if (aiQuestionInput) aiQuestionInput.disabled = !enabled;
        if (aiAskBtn) aiAskBtn.disabled = !enabled;
        if (readerJumpInput) readerJumpInput.disabled = !enabled;
        if (readerJumpBtn) readerJumpBtn.disabled = !enabled;
    }

    function renderNotesSelect(list) {
        if (!notesSelect) return;
        const arr = Array.isArray(list) ? list : [];
        if (arr.length === 0) {
            notesSelect.innerHTML = `<option value="">暂无笔记</option>`;
            notesSelect.value = '';
            return;
        }
        // 下拉不展示时间（时间放到下方编辑区右上角）
        notesSelect.innerHTML = arr.map((n, idx) => {
            const id = n && n.id ? String(n.id) : '';
            return `<option value="${escapeHtml(id)}">笔记${idx + 1}</option>`;
        }).join('');
    }

    function formatNoteTime(d) {
        try {
            const dt = new Date(d);
            if (Number.isNaN(dt.getTime())) return '';
            const pad = (n) => String(n).padStart(2, '0');
            return `${pad(dt.getMonth() + 1)}-${pad(dt.getDate())} ${pad(dt.getHours())}:${pad(dt.getMinutes())}`;
        } catch (_) {
            return '';
        }
    }

    function applyNoteToEditor(noteId) {
        if (!bookshelfNotesInput) return;
        const id = noteId ? String(noteId) : '';
        const found = currentNotesList.find(n => String(n.id) === id);
        if (!found) return;
        draftNewNote = false;
        currentNoteId = found.id;
        bookshelfNotesInput.value = found.content || '';
        // 不展示“正在编辑：笔记（ID …）”，避免标题栏显得突兀
        if (bookshelfNotesStatus) bookshelfNotesStatus.textContent = '';
        if (notesUpdateTime) {
            const t = formatNoteTime(found.updateTime || found.createTime);
            notesUpdateTime.textContent = t ? `更新于 ${t}` : '';
        }
        const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
        if (userName && userName !== '登陆') {
            scheduleSaveHisAction(userName);
        }
    }

    function scheduleSaveHisAction(userName) {
        if (!userName || userName === '登陆') return;
        if (hisActionSaveTimer) clearTimeout(hisActionSaveTimer);
        hisActionSaveTimer = setTimeout(() => {
            if (!selectedBookId) return;
            const params = new URLSearchParams();
            params.set('userName', String(userName));
            params.set('bookId', String(selectedBookId));
            if (currentNoteId) params.set('noteId', String(currentNoteId));
            params.set('bookName', String(selectedBookDisplayName || selectedBookFileName || currentBookName || ''));
            params.set('pageNo', String(currentBookPageIndex || 1));
            if (aiSelectedContext) params.set('context', String(aiSelectedContext));

            fetch(`${API_BASE_URL}/user/his-action/save`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                body: params.toString()
            }).catch(() => {});
        }, 400);
    }

    function tryRestoreHisAction(userName) {
        if (hisActionRestoreDone || hisActionRestoreInFlight) return;
        if (!userName || userName === '登陆') return;
        hisActionRestoreInFlight = true;
        fetch(`${API_BASE_URL}/user/his-action/get`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
            body: new URLSearchParams({ userName: String(userName) }).toString()
        })
        .then(res => res.json())
        .then(data => {
            hisActionRestoreInFlight = false;
            // 只要请求成功返回（无论有没有历史），就不再重复恢复，避免每次刷新书架都请求一次
            hisActionRestoreDone = true;
            if (!(data && (data.code === "0" || data.code === "200"))) return;
            let payload = data.data || null;
            if (typeof payload === 'string') {
                try { payload = JSON.parse(payload); } catch (e) { payload = null; }
            }
            if (!payload) return;
            const bookId = payload.bookId;
            if (!bookId) return;
            const it = (bookshelfAllBooks || []).find(x => String(x && (x.bookId ?? x.id)) === String(bookId));
            if (!it) return;
            const fileName = it && it.fileName ? it.fileName : '';
            const displayName = stripFileExt(fileName) || (payload.bookName || '正文');
            const pageCount = it && typeof it.pageCount !== 'undefined' ? (it.pageCount || 0) : 0;
            const pageNo = payload.pageNo ? parseInt(payload.pageNo, 10) : 1;
            const noteId = payload.noteId ? payload.noteId : null;
            selectBookInWorkspace(
                userName,
                bookId,
                fileName,
                displayName,
                pageCount,
                Number.isFinite(pageNo) ? pageNo : 1,
                noteId
            );
        })
        .catch(() => {
            // 请求失败不要置 done=true，允许用户刷新/再次进入书架重试
            hisActionRestoreInFlight = false;
        });
    }

    function loadNotesForBookFromServer(userName, bookId) {
        if (!userName || !bookId) return;
        fetch(`${API_BASE_URL}/note/list`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
            body: new URLSearchParams({ userName: String(userName), bookId: String(bookId) }).toString()
        })
        .then(res => res.json())
        .then(data => {
            if (!(data && (data.code === "0" || data.code === "200"))) return;
            let list = data.data || [];
            if (typeof list === 'string') {
                try { list = JSON.parse(list); } catch (e) { list = []; }
            }
            currentNotesList = Array.isArray(list) ? list : [];
            // 笔记编号（笔记1/2/3）按 note.id 从小到大稳定排序
            currentNotesList.sort((a, b) => {
                const ai = parseInt((a && a.id) != null ? String(a.id) : '0', 10) || 0;
                const bi = parseInt((b && b.id) != null ? String(b.id) : '0', 10) || 0;
                return ai - bi;
            });
            renderNotesSelect(currentNotesList);
            if (currentNotesList.length > 0) {
                // 刷新列表时优先保持当前选中笔记，否则默认选中 id 最小的（笔记1）
                const preferredId = (currentNoteId && currentNotesList.some(n => String(n && n.id) === String(currentNoteId)))
                    ? currentNoteId
                    : currentNotesList[0].id;
                if (notesSelect) notesSelect.value = String(preferredId);
                
                // 检查用户是否正在编辑：如果输入框内容与服务器内容不同，说明用户正在编辑，不要覆盖
                const found = currentNotesList.find(n => String(n.id) === String(preferredId));
                const currentInputValue = bookshelfNotesInput ? (bookshelfNotesInput.value || '').trim() : '';
                const serverContent = found ? (found.content || '').trim() : '';
                
                // 只有在以下情况才应用笔记到编辑器：
                // 1. 用户没有在编辑（输入框内容与服务器内容相同）
                // 2. 或者切换到了不同的笔记
                // 3. 或者输入框为空
                if (currentInputValue === serverContent || String(currentNoteId) !== String(preferredId) || !currentInputValue) {
                    applyNoteToEditor(preferredId);
                } else {
                    // 用户正在编辑，只更新 currentNoteId，不覆盖输入框内容
                    currentNoteId = preferredId;
                }
            } else {
                currentNoteId = null;
                draftNewNote = false;
                if (bookshelfNotesInput) bookshelfNotesInput.value = '';
                if (bookshelfNotesStatus) bookshelfNotesStatus.textContent = '暂无笔记（可直接输入，自动保存）';
                if (notesUpdateTime) notesUpdateTime.textContent = '';
            }
        })
        .catch(() => {
            // ignore
        });
    }

    function formatTimeHHMMSS(d) {
        const pad = (n) => String(n).padStart(2, '0');
        return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
    }

    function scheduleSaveNotes(userName, bookId) {
        if (!bookshelfNotesInput) return;
        if (notesSaveTimer) clearTimeout(notesSaveTimer);
        notesSaveTimer = setTimeout(() => {
            // 仍保留一份本地备份，方便断网
            try {
                const key = `notes:${String(userName || '')}:${String(bookId || '')}`;
            localStorage.setItem(key, bookshelfNotesInput.value || '');
            } catch (_) {}
            if (notesUpdateTime) {
                notesUpdateTime.textContent = `已保存 ${formatTimeHHMMSS(new Date())}`;
                notesUpdateTime.classList.add('saving');
                setTimeout(() => {
                    if (notesUpdateTime) {
                        notesUpdateTime.classList.remove('saving');
                        notesUpdateTime.classList.add('saved');
                        setTimeout(() => {
                            if (notesUpdateTime) notesUpdateTime.classList.remove('saved');
                        }, 1000);
                    }
                }, 100);
            }
            if (bookshelfNotesStatus) bookshelfNotesStatus.textContent = `本地已保存 ${formatTimeHHMMSS(new Date())}`;
        }, 300);

        // 草稿模式：不自动入库，等待用户点击“暂存”才创建记录
        if (draftNewNote && !currentNoteId) {
            if (bookshelfNotesStatus) bookshelfNotesStatus.textContent = '新笔记草稿（暂存后入库）';
            return;
        }

        // 同步保存到后端（debounce，更新已有笔记 / 非草稿新建）
        if (notesServerSaveTimer) clearTimeout(notesServerSaveTimer);
        notesServerSaveTimer = setTimeout(() => {
            const content = (bookshelfNotesInput.value || '').trim();
            if (!content) return; // 后端不允许空内容
            const params = new URLSearchParams();
            params.set('userName', String(userName));
            params.set('bookId', String(bookId));
            if (currentNoteId) params.set('noteId', String(currentNoteId));
            params.set('content', content);
            fetch(`${API_BASE_URL}/note/save`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                body: params.toString()
            })
            .then(res => res.json())
            .then(data => {
                if (!(data && (data.code === "0" || data.code === "200"))) {
                    if (bookshelfNotesStatus) bookshelfNotesStatus.textContent = `云端保存失败`;
                    return;
                }
                let payload = data.data || {};
                if (typeof payload === 'string') {
                    try { payload = JSON.parse(payload); } catch (e) { payload = {}; }
                }
                if (payload && payload.noteId) {
                    currentNoteId = payload.noteId;
                }
                const saveTime = formatTimeHHMMSS(new Date());
                if (bookshelfNotesStatus) bookshelfNotesStatus.textContent = `${data.message || '云端已保存'} ${saveTime}`;
                if (notesUpdateTime) {
                    notesUpdateTime.textContent = `已保存 ${saveTime}`;
                    notesUpdateTime.classList.add('saving');
                    setTimeout(() => {
                        if (notesUpdateTime) {
                            notesUpdateTime.classList.remove('saving');
                            notesUpdateTime.classList.add('saved');
                            setTimeout(() => {
                                if (notesUpdateTime) notesUpdateTime.classList.remove('saved');
                            }, 1500);
                        }
                    }, 100);
                }
                if (payload && payload.deletedOldest) {
                    showToast('最多存 3 个笔记，已自动删除最早的一个笔记。', 'info', 5000);
                }
                // 保存后刷新下拉（确保顺序/数量正确），但不覆盖正在编辑的内容
                // 延迟刷新，避免与用户输入冲突
                setTimeout(() => {
                    loadNotesForBookFromServer(userName, bookId);
                }, 100);
            })
            .catch(() => {
                if (bookshelfNotesStatus) bookshelfNotesStatus.textContent = `云端保存失败`;
            });
        }, 600);
    }

    function showBookshelf() {
        if (bookshelfContainer) bookshelfContainer.classList.remove('hidden');
    }

    function hideBookshelf() {
        if (bookshelfContainer) bookshelfContainer.classList.add('hidden');
        if (bookshelfList) bookshelfList.innerHTML = '';
        if (bookshelfEmpty) bookshelfEmpty.classList.add('hidden');
        if (bookshelfPagination) bookshelfPagination.classList.add('hidden');
    }

    function renderBookshelf(items) {
        if (!bookshelfList) return;
        showBookshelf();
        bookshelfFilteredBooks = Array.isArray(items) ? items : [];
        bookshelfCurrentPage = 1;
        renderBookshelfPage();
    }

    function getFileExt(fileName) {
        const lower = String(fileName || '').toLowerCase();
        const dot = lower.lastIndexOf('.');
        return dot >= 0 ? lower.substring(dot + 1) : '';
    }

    function stripFileExt(fileName) {
        const s = String(fileName || '');
        const dot = s.lastIndexOf('.');
        if (dot <= 0) return s;
        return s.substring(0, dot);
    }

    // 为每本书分配一个稳定的封面主题色（同一本书每次进来颜色一致）
    const BOOK_COVER_THEMES = ['t0', 't1', 't2', 't3', 't4', 't5', 't6', 't7', 't8'];
    function hashStringToInt(str) {
        const s = String(str || '');
        let h = 0;
        for (let i = 0; i < s.length; i++) {
            h = ((h << 5) - h) + s.charCodeAt(i);
            h |= 0; // 32-bit
        }
        return Math.abs(h);
    }
    function pickBookCoverTheme(key) {
        const idx = BOOK_COVER_THEMES.length > 0
            ? (hashStringToInt(key) % BOOK_COVER_THEMES.length)
            : 0;
        return BOOK_COVER_THEMES[idx] || 't0';
    }

    function renderBookshelfPage() {
        const total = bookshelfFilteredBooks.length;
        if (total === 0) {
            if (bookshelfList) bookshelfList.innerHTML = '';
            if (bookshelfEmpty) bookshelfEmpty.classList.remove('hidden');
            if (bookshelfPagination) bookshelfPagination.classList.add('hidden');
            return;
        }
        if (bookshelfEmpty) bookshelfEmpty.classList.add('hidden');

        const totalPages = Math.max(1, Math.ceil(total / bookshelfPageSize));
        if (bookshelfCurrentPage > totalPages) bookshelfCurrentPage = totalPages;
        if (bookshelfCurrentPage < 1) bookshelfCurrentPage = 1;

        const start = (bookshelfCurrentPage - 1) * bookshelfPageSize;
        const pageItems = bookshelfFilteredBooks.slice(start, start + bookshelfPageSize);

        bookshelfList.innerHTML = pageItems.map((it) => {
            const bookId = it && (it.bookId ?? it.id);
            const fileName = (it && it.fileName) ? it.fileName : '未命名';
            const displayName = stripFileExt(fileName);
            const themeKey = (bookId !== null && typeof bookId !== 'undefined') ? String(bookId) : fileName;
            const theme = pickBookCoverTheme(themeKey);
            const pageCount = it && typeof it.pageCount !== 'undefined' ? (it.pageCount || 0) : 0;
            const activeCls = (selectedBookId !== null && String(selectedBookId) === String(bookId)) ? 'active' : '';
            return `
                <div class="book-list-item ${activeCls}" data-book-id="${escapeHtml(bookId)}" data-book-name="${escapeHtml(fileName)}" data-book-display="${escapeHtml(displayName)}" data-page-count="${pageCount}">
                    <div class="book-meta">
                        <div class="book-name" title="${escapeHtml(displayName)}">
                            ${escapeHtml(displayName)} <span class="book-pages">(${pageCount}页)</span>
                        </div>
                    </div>
                    <div class="book-actions">
                        <button type="button" class="book-delete-btn" data-action="delete" title="删除">删除</button>
                    </div>
                </div>
            `;
        }).join('');

        bookshelfList.querySelectorAll('[data-book-id]').forEach((el) => {
            el.addEventListener('click', () => {
                // 点击删除按钮不触发“选书”
                // （实际拦截在 deleteBtn 的 click 里，这里保留原逻辑）
                const bookId = el.getAttribute('data-book-id');
                const bookName = el.getAttribute('data-book-name') || '';
                const displayName = el.getAttribute('data-book-display') || stripFileExt(bookName);
                const pageCountStr = el.getAttribute('data-page-count') || '0';
                const pageCount = parseInt(pageCountStr, 10);
                const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
                if (!userName || userName === '登陆') return;
                if (!bookId) return;
                selectBookInWorkspace(userName, bookId, bookName, displayName, Number.isFinite(pageCount) ? pageCount : 0);
            });
        });

        // 删除按钮：二次确认 + 调用后端删除（不可恢复）
        bookshelfList.querySelectorAll('.book-delete-btn[data-action="delete"]').forEach((btn) => {
            btn.addEventListener('click', (ev) => {
                ev.preventDefault();
                ev.stopPropagation();
                const row = btn.closest('.book-list-item');
                if (!row) return;
                const bookId = row.getAttribute('data-book-id');
                const displayName = row.getAttribute('data-book-display') || row.getAttribute('data-book-name') || '该书籍';
                const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
                if (!userName || userName === '登陆') return;
                if (!bookId) return;

                const ok = window.confirm(`是否删除《${displayName}》？\n删除后无法恢复。`);
                if (!ok) return;

                // 如果正在阅读/编辑这本书：先重置工作台，避免残留状态
                if (selectedBookId !== null && String(selectedBookId) === String(bookId)) {
                    resetBookshelfWorkspace();
                }

                btn.disabled = true;
                const oldText = btn.textContent;
                btn.textContent = '删除中';

                const params = new URLSearchParams();
                params.set('userName', String(userName));
                params.set('bookId', String(bookId));
                fetch(`${API_BASE_URL}/ai-reading/book/delete`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                    body: params.toString()
                })
                .then(res => res.json())
                .then(data => {
                    if (!(data && (data.code === "0" || data.code === "200"))) {
                        const msg = (data && data.message) ? data.message : '删除失败';
                        showToast(msg, 'error');
                        return;
                    }
                    // 删除成功：刷新列表（后端为准）
                    return loadBookshelf(userName);
                })
                .finally(() => {
                    btn.disabled = false;
                    btn.textContent = oldText;
                });
            });
        });

        renderBookshelfPagination(totalPages);
    }

    function updateReaderNavState() {
        const total = selectedBookTotalPages || 0;
        const pageNo = currentBookPageIndex || 0;
        if (readerPageInfo) readerPageInfo.textContent = `第 ${pageNo}/${total} 页`;
        if (readerPrevPageBtn) readerPrevPageBtn.disabled = (pageNo <= 1);
        if (readerNextPageBtn) readerNextPageBtn.disabled = (total <= 0 || pageNo >= total);
        if (readerJumpInput) {
            readerJumpInput.min = '1';
            readerJumpInput.max = String(Math.max(1, total || 1));
        }
    }

    function loadWorkspaceReaderPage(userName, bookId, pageNo) {
        if (!userName || !bookId || !pageNo) return;
        currentBookPageIndex = pageNo;
        updateReaderNavState();
        if (readerContent) readerContent.innerHTML = `<div class="bookshelf-placeholder">加载中...</div>`;
        scheduleSaveHisAction(userName);

        fetch(`${API_BASE_URL}/ai-reading/book/page?userName=${encodeURIComponent(userName)}&bookId=${encodeURIComponent(bookId)}&pageNo=${encodeURIComponent(pageNo)}`, { method: 'POST' })
            .then(res => res.json())
            .then(data => {
                if (!(data && (data.code === "0" || data.code === "200"))) {
                    const msg = (data && data.message) ? data.message : '加载失败';
                    if (readerContent) readerContent.innerHTML = `<div class="bookshelf-placeholder">${escapeHtml(msg)}</div>`;
                    return;
                }
                let payload = data.data || {};
                if (typeof payload === 'string') {
                    try { payload = JSON.parse(payload); } catch (e) { payload = {}; }
                }
                const totalPages = payload.totalPages || selectedBookTotalPages || 0;
                selectedBookTotalPages = totalPages;
                currentBookPageIndex = payload.pageNo || pageNo;
                updateReaderNavState();
                const text = typeof payload.oriText !== 'undefined' ? payload.oriText : '';
                if (readerContent) {
                    const { body, notes } = splitBodyAndNotes(text);
                    const notesHtml = (notes && notes.trim()) ? `<pre style="white-space:pre-wrap;">${escapeHtml(notes)}</pre>` : '<p class="reader-notes-placeholder">本页无注释内容</p>';
                    readerContent.innerHTML = `
                        <div class="reader-split-wrapper">
                            <div class="reader-body-panel"><pre style="white-space:pre-wrap;">${escapeHtml(body)}</pre></div>
                            <div class="reader-notes-panel">${notesHtml}</div>
                        </div>`;
                }
            })
            .catch(() => {
                if (readerContent) readerContent.innerHTML = `<div class="bookshelf-placeholder">加载失败，请稍后重试</div>`;
            });
    }

    function selectBookInWorkspace(userName, bookId, bookName, displayName, pageCount, initialPageNo, initialNoteId) {
        selectedBookId = bookId;
        selectedBookFileName = bookName || '';
        currentBookName = bookName || '';
        selectedBookDisplayName = displayName || '';
        selectedBookTotalPages = pageCount || 0;
        if (bookshelfReaderTitle) bookshelfReaderTitle.textContent = displayName || '正文';
        setWorkspaceEnabledForBook(true);
        // 切书时避免把上一本书的 noteId 带过来；恢复时可传入 initialNoteId
        draftNewNote = false;
        currentNoteId = initialNoteId ? initialNoteId : null;
        loadNotesForBookFromServer(userName, bookId);
        // 切换书籍时清空“选中文本上下文”，避免串书
        clearAiSelectedContext();
        hideAiSelectionBar();

        // 高亮当前选中
        if (bookshelfList) {
            bookshelfList.querySelectorAll('.book-list-item').forEach((x) => x.classList.remove('active'));
            const current = bookshelfList.querySelector(`.book-list-item[data-book-id="${CSS.escape(String(bookId))}"]`);
            if (current) current.classList.add('active');
        }

        if (!selectedBookTotalPages) {
            // 如果 books 接口没带 pageCount，就先按 1/1 显示，真正总数以后端为准
            selectedBookTotalPages = 1;
        }
        const p = initialPageNo && Number.isFinite(parseInt(initialPageNo, 10)) ? parseInt(initialPageNo, 10) : 1;
        loadWorkspaceReaderPage(userName, bookId, Math.max(1, p));
        scheduleSaveHisAction(userName);
    }

    function renderBookshelfPagination(totalPages) {
        if (!bookshelfPagination || !bookshelfPageNumbers || !bookshelfPrevBtn || !bookshelfNextBtn) return;
        bookshelfPagination.classList.remove('hidden');
        bookshelfPrevBtn.disabled = bookshelfCurrentPage <= 1;
        bookshelfNextBtn.disabled = bookshelfCurrentPage >= totalPages;

        // 页码显示：最多 7 个按钮，带省略号
        const mkBtn = (label, page, active = false, disabled = false) => {
            const cls = `page-btn page-number ${active ? 'active' : ''}`;
            return `<button type="button" class="${cls}" data-page="${page}" ${disabled ? 'disabled' : ''}>${label}</button>`;
        };

        let html = '';
        if (totalPages <= 7) {
            for (let i = 1; i <= totalPages; i++) html += mkBtn(i, i, i === bookshelfCurrentPage);
        } else {
            const p = bookshelfCurrentPage;
            html += mkBtn(1, 1, p === 1);
            if (p > 3) html += `<span style="padding:0 6px;color:#aaa;">…</span>`;
            const start = Math.max(2, p - 1);
            const end = Math.min(totalPages - 1, p + 1);
            for (let i = start; i <= end; i++) html += mkBtn(i, i, i === p);
            if (p < totalPages - 2) html += `<span style="padding:0 6px;color:#aaa;">…</span>`;
            html += mkBtn(totalPages, totalPages, p === totalPages);
        }
        bookshelfPageNumbers.innerHTML = html;

        bookshelfPageNumbers.querySelectorAll('[data-page]').forEach((btn) => {
            btn.addEventListener('click', () => {
                const p = parseInt(btn.getAttribute('data-page'), 10);
                if (!Number.isFinite(p)) return;
                bookshelfCurrentPage = p;
                renderBookshelfPage();
            });
        });
    }

    function loadBookshelf(userName) {
        if (!userName) return Promise.resolve();
        return fetch(`${API_BASE_URL}/ai-reading/books?userName=${encodeURIComponent(userName)}`, { method: 'POST' })
            .then(res => res.json())
            .then(data => {
                if (!(data && (data.code === "0" || data.code === "200"))) return;
                let list = data.data || [];
                if (typeof list === 'string') {
                    try { list = JSON.parse(list); } catch (e) { list = []; }
                }
                bookshelfAllBooks = Array.isArray(list) ? list : [];
                // 初始：按当前搜索条件过滤并渲染
                applyBookshelfFilterAndRender();
                // books 列表加载完成后尝试恢复上次选择
                tryRestoreHisAction(userName);
            })
            .catch(() => {
                // ignore
            });
    }

    function applyBookshelfFilterAndRender() {
        const keyword = (bookshelfSearchInput && bookshelfSearchInput.value)
            ? bookshelfSearchInput.value.trim().toLowerCase()
            : '';
        const filtered = keyword
            ? bookshelfAllBooks.filter(it => String((it && it.fileName) ? it.fileName : '').toLowerCase().includes(keyword))
            : bookshelfAllBooks;
        renderBookshelf(filtered);
    }

    function closeBookReaderModal() {
        if (!bookReaderModal) return;
        bookReaderModal.classList.remove('show');
        document.body.style.overflow = 'auto';
        currentBookPages = [];
        currentBookPageIndex = 0;
        currentBookName = '';
    }

    function openBookReader(userName, bookId, bookName) {
        if (!bookReaderModal) return;
        currentBookPages = [];
        currentBookPageIndex = 0;
        currentBookName = bookName || '阅读';
        if (bookReaderTitle) bookReaderTitle.textContent = currentBookName;
        if (bookReaderPageInfo) bookReaderPageInfo.textContent = '第 0/0 页';
        if (bookReaderContent) {
            bookReaderContent.innerHTML = `
                <div class="article-detail-loading">
                    <div class="loading-spinner"></div>
                    <p>加载中...</p>
                </div>
            `;
        }
        bookReaderModal.classList.add('show');
        document.body.style.overflow = 'hidden';

        fetch(`${API_BASE_URL}/ai-reading/book/pages?userName=${encodeURIComponent(userName)}&bookId=${encodeURIComponent(bookId)}`, { method: 'POST' })
            .then(res => res.json())
            .then(data => {
                if (!(data && (data.code === "0" || data.code === "200"))) {
                    const msg = (data && data.message) ? data.message : '加载失败';
                    if (bookReaderContent) bookReaderContent.innerHTML = `<div class="article-detail-error"><p>${escapeHtml(msg)}</p></div>`;
                    return;
                }
                let payload = data.data || {};
                if (typeof payload === 'string') {
                    try { payload = JSON.parse(payload); } catch (e) { payload = {}; }
                }
                let pages = payload.pages || [];
                if (typeof pages === 'string') {
                    try { pages = JSON.parse(pages); } catch (e) { pages = []; }
                }
                currentBookPages = Array.isArray(pages) ? pages : [];
                currentBookPageIndex = 0;
                renderCurrentBookPage();
            })
            .catch(() => {
                if (bookReaderContent) bookReaderContent.innerHTML = `<div class="article-detail-error"><p>加载失败，请稍后重试</p></div>`;
            });
    }

    function renderCurrentBookPage() {
        const total = currentBookPages.length;
        const idx = Math.max(0, Math.min(currentBookPageIndex, Math.max(0, total - 1)));
        currentBookPageIndex = idx;

        const page = total > 0 ? currentBookPages[idx] : null;
        const pageNo = page && page.pageNo ? page.pageNo : (total > 0 ? (idx + 1) : 0);
        if (bookReaderPageInfo) bookReaderPageInfo.textContent = `第 ${pageNo}/${total} 页`;

        if (bookPrevPageBtn) bookPrevPageBtn.disabled = (idx <= 0);
        if (bookNextPageBtn) bookNextPageBtn.disabled = (idx >= total - 1);

        const text = page && typeof page.oriText !== 'undefined' ? page.oriText : '';
        if (bookReaderContent) {
            const { body, notes } = splitBodyAndNotes(text);
            const notesHtml = (notes && notes.trim()) ? `<pre style="white-space:pre-wrap;">${escapeHtml(notes)}</pre>` : '<p class="reader-notes-placeholder">本页无注释内容</p>';
            bookReaderContent.innerHTML = `
                <div class="reader-split-wrapper">
                    <div class="reader-body-panel"><pre style="white-space:pre-wrap;">${escapeHtml(body)}</pre></div>
                    <div class="reader-notes-panel">${notesHtml}</div>
                </div>`;
        }
    }

    function renderParsingProgressList(items) {
        const listEl = document.getElementById('parsingProgressList');
        const summaryEl = document.getElementById('parsingProgressSummary');
        if (!listEl) return;

        const safeItems = Array.isArray(items) ? items : [];
        if (summaryEl) summaryEl.textContent = `${safeItems.length} 个任务`;

        listEl.innerHTML = safeItems.map((it) => {
            const fileName = (it && it.fileName) ? it.fileName : '未知文件';
            const progressNum = (it && typeof it.progress !== 'undefined') ? parseFloat(it.progress) : 0;
            const progress = Number.isFinite(progressNum) ? Math.max(0, Math.min(100, progressNum)) : 0;
            const status = progress >= 100 ? '解析完成' : `解析中... ${Math.round(progress)}%`;
            return `
                <div class="parsing-progress-item">
                    <div class="upload-progress-header">
                        <span class="upload-progress-title">任务</span>
                        <span class="upload-progress-percentage">${Math.round(progress)}%</span>
                    </div>
                    <div class="parsing-progress-filename">正在解析: ${escapeHtml(fileName)}</div>
                    <div class="upload-progress-bar">
                        <div class="upload-progress-fill" style="width: ${progress}%"></div>
                    </div>
                    <div class="upload-progress-status">${escapeHtml(status)}</div>
                </div>
            `;
        }).join('');
    }

    function resetUploadProgress() {
        updateUploadProgress(0, '准备上传...');
    }

    function updateUploadProgress(percentage, status) {
        const uploadProgressFill = document.getElementById('uploadProgressFill');
        const uploadProgressPercentage = document.getElementById('uploadProgressPercentage');
        const uploadProgressStatus = document.getElementById('uploadProgressStatus');

        if (uploadProgressFill) {
            uploadProgressFill.style.width = percentage + '%';
        }
        if (uploadProgressPercentage) {
            uploadProgressPercentage.textContent = Math.round(percentage) + '%';
        }
        if (uploadProgressStatus) {
            uploadProgressStatus.textContent = status || '上传中...';
        }
    }

    function showAiReadingTab() {
        if (aiReadingTab) {
            aiReadingTab.classList.remove('hidden');
        }
    }

    function showBookshelfTab() {
        if (bookshelfTab) {
            bookshelfTab.classList.remove('hidden');
        }
    }

    function hideBookshelfTab() {
        if (bookshelfTab) {
            bookshelfTab.classList.add('hidden');
            bookshelfTab.classList.remove('active');
        }
    }

    function hideAiReadingTab() {
        if (aiReadingTab) {
            aiReadingTab.classList.add('hidden');
            aiReadingTab.classList.remove('active');
        }
        hideBookshelfTab();
        showUploadMessage('', true);
        hideUploadProgress();
        hideParsingProgress(); // Reset parsing progress
        hideBookshelf();
        switchToHome();
    }

    hideAiReadingTab();

    if (homeTab) {
        homeTab.addEventListener('click', function(e) {
            e.preventDefault();
            switchToHome();
        });
    }

    if (aiReadingTab) {
        aiReadingTab.addEventListener('click', function(e) {
            e.preventDefault();
            switchToAiReading();
        });
    }

    if (bookshelfTab) {
        bookshelfTab.addEventListener('click', function(e) {
            e.preventDefault();
            switchToBookshelf();
        });
    }

    if (refreshBookshelfBtn) {
        let refreshSpinTimer = null;
        refreshBookshelfBtn.addEventListener('click', function() {
            const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
            if (!userName || userName === '登陆') return;
            refreshBookshelfBtn.classList.add('spinning');
            refreshBookshelfBtn.disabled = true;
            const start = (typeof performance !== 'undefined' && performance.now) ? performance.now() : Date.now();
            const MIN_SPIN_MS = 800; // 至少转满 1 圈（与 CSS 动画时长保持一致）
            Promise.resolve(loadBookshelf(userName))
                .finally(() => {
                    const end = (typeof performance !== 'undefined' && performance.now) ? performance.now() : Date.now();
                    const elapsed = Math.max(0, end - start);
                    const remain = Math.max(0, MIN_SPIN_MS - elapsed);
                    if (refreshSpinTimer) {
                        clearTimeout(refreshSpinTimer);
                        refreshSpinTimer = null;
                    }
                    refreshSpinTimer = setTimeout(() => {
                        refreshBookshelfBtn.classList.remove('spinning');
                        refreshBookshelfBtn.disabled = false;
                        refreshSpinTimer = null;
                    }, remain);
                });
        });
    }

    if (bookshelfPrevBtn) {
        bookshelfPrevBtn.addEventListener('click', function() {
            bookshelfCurrentPage = Math.max(1, bookshelfCurrentPage - 1);
            renderBookshelfPage();
        });
    }
    if (bookshelfNextBtn) {
        bookshelfNextBtn.addEventListener('click', function() {
            bookshelfCurrentPage = bookshelfCurrentPage + 1;
            renderBookshelfPage();
        });
    }

    if (readerPrevPageBtn) {
        readerPrevPageBtn.addEventListener('click', function() {
            const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
            if (!userName || userName === '登陆') return;
            if (!selectedBookId) return;
            const target = Math.max(1, (currentBookPageIndex || 1) - 1);
            loadWorkspaceReaderPage(userName, selectedBookId, target);
        });
    }

    if (readerNextPageBtn) {
        readerNextPageBtn.addEventListener('click', function() {
            const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
            if (!userName || userName === '登陆') return;
            if (!selectedBookId) return;
            const total = selectedBookTotalPages || 0;
            const target = Math.min(total > 0 ? total : (currentBookPageIndex || 1) + 1, (currentBookPageIndex || 1) + 1);
            loadWorkspaceReaderPage(userName, selectedBookId, target);
        });
    }

    function jumpToPage() {
        const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
        if (!userName || userName === '登陆') return;
        if (!selectedBookId) return;
        if (!readerJumpInput) return;
        const raw = parseInt(readerJumpInput.value, 10);
        if (!Number.isFinite(raw)) return;
        const total = selectedBookTotalPages || 0;
        const p = total > 0 ? Math.max(1, Math.min(total, raw)) : Math.max(1, raw);
        readerJumpInput.value = String(p);
        loadWorkspaceReaderPage(userName, selectedBookId, p);
    }

    if (readerJumpBtn) {
        readerJumpBtn.addEventListener('click', jumpToPage);
    }
    if (readerJumpInput) {
        readerJumpInput.addEventListener('keydown', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                jumpToPage();
            }
        });
    }

    if (bookshelfNotesInput) {
        bookshelfNotesInput.addEventListener('input', function() {
            const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
            if (!userName || userName === '登陆') return;
            if (!selectedBookId) return;
            scheduleSaveNotes(userName, selectedBookId);
        });
    }

    if (notesSelect) {
        notesSelect.addEventListener('change', function() {
            const id = (notesSelect.value || '').trim();
            if (!id) return;
            applyNoteToEditor(id);
        });
    }

    if (newNoteBtn) {
        newNoteBtn.addEventListener('click', function() {
            const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
            if (!userName || userName === '登陆') return;
            if (!selectedBookId) return;
            if (!bookshelfNotesInput) return;
            // 进入“新笔记草稿”：清空编辑区，但不入库，等待暂存
            draftNewNote = true;
            currentNoteId = null;
            bookshelfNotesInput.value = '';
            if (notesSelect) notesSelect.value = '';
            if (notesUpdateTime) notesUpdateTime.textContent = '';
            if (bookshelfNotesStatus) bookshelfNotesStatus.textContent = '新笔记草稿（暂存后入库）';
            bookshelfNotesInput.focus();
        });
    }

    if (stashNoteBtn) {
        stashNoteBtn.addEventListener('click', function() {
            const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
            if (!userName || userName === '登陆') return;
            if (!selectedBookId) return;
            if (!bookshelfNotesInput) return;
            const content = (bookshelfNotesInput.value || '').trim();
            if (!content) {
                showToast('当前笔记为空，无需暂存', 'info');
                return;
            }
            // 暂存规则：
            // - 正在编辑已有笔记：只更新当前选中的笔记
            // - 新增草稿：此时才创建一条新的笔记记录
            const params = new URLSearchParams();
            params.set('userName', String(userName));
            params.set('bookId', String(selectedBookId));
            params.set('content', content);
            if (currentNoteId) {
                params.set('noteId', String(currentNoteId));
            }

            fetch(`${API_BASE_URL}/note/save`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                body: params.toString()
            })
            .then(res => res.json())
            .then(data => {
                if (!(data && (data.code === "0" || data.code === "200"))) {
                    showToast((data && data.message) ? data.message : '暂存失败', 'error');
                    return;
                }
                let payload = data.data || {};
                if (typeof payload === 'string') {
                    try { payload = JSON.parse(payload); } catch (e) { payload = {}; }
                }
                if (payload && payload.deletedOldest) {
                    showToast('最多存 3 个笔记，已自动删除最早的一个笔记。', 'info', 5000);
                }
                // 如果是草稿暂存：保存后切回“编辑该笔记”
                if (!currentNoteId && payload && payload.noteId) {
                    currentNoteId = payload.noteId;
                }
                draftNewNote = false;
                const saveTime = formatTimeHHMMSS(new Date());
                if (bookshelfNotesStatus) bookshelfNotesStatus.textContent = `${data.message || '已暂存'} ${saveTime}`;
                if (notesUpdateTime) {
                    notesUpdateTime.textContent = `已保存 ${saveTime}`;
                    notesUpdateTime.classList.add('saving');
                    setTimeout(() => {
                        if (notesUpdateTime) {
                            notesUpdateTime.classList.remove('saving');
                            notesUpdateTime.classList.add('saved');
                            setTimeout(() => {
                                if (notesUpdateTime) notesUpdateTime.classList.remove('saved');
                            }, 1500);
                        }
                    }, 100);
                }
                loadNotesForBookFromServer(userName, selectedBookId);
            })
            .catch(() => showToast('暂存失败：网络错误或后端未启动', 'error'));
        });
    }

    function sanitizeFileName(name) {
        const s = String(name || '').trim() || '笔记';
        // Windows/macOS 常见非法字符：\/:*?"<>|
        return s.replace(/[\\\/:*?"<>|]/g, '_').replace(/\s+/g, ' ').trim().slice(0, 80);
    }

    function wrapLongLine(line, maxLen) {
        const s = String(line || '');
        if (!maxLen || maxLen <= 0) return s;
        if (s.length <= maxLen) return s;
        const parts = [];
        for (let i = 0; i < s.length; i += maxLen) {
            parts.push(s.slice(i, i + maxLen));
        }
        return parts.join('\n');
    }

    function formatNoteTextForExport(raw) {
        let text = String(raw || '');
        text = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
        // 如果整段没有任何换行，做一个简单折行，避免“一坨”
        if (!text.includes('\n')) {
            text = wrapLongLine(text, 80);
        }
        // 额外处理：把连续 3 个以上空行压到 2 个
        text = text.replace(/\n{3,}/g, '\n\n');
        return text.trim() + '\n';
    }

    async function ensureNoteSavedForExport(userName) {
        if (!selectedBookId || !bookshelfNotesInput) return null;
        const content = (bookshelfNotesInput.value || '').trim();
        if (!content) return null;
        // 若已有 noteId：先确保云端保存一次（避免导出旧内容）
        const params = new URLSearchParams();
        params.set('userName', String(userName));
        params.set('bookId', String(selectedBookId));
        params.set('content', content);
        if (currentNoteId) {
            params.set('noteId', String(currentNoteId));
        }
        const resp = await fetch(`${API_BASE_URL}/note/save`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
            body: params.toString()
        });
        const data = await resp.json();
        if (!(data && (data.code === "0" || data.code === "200"))) return null;
        let payload = data.data || {};
        if (typeof payload === 'string') {
            try { payload = JSON.parse(payload); } catch (e) { payload = {}; }
        }
        if (payload && payload.noteId) {
            currentNoteId = payload.noteId;
        }
        // 保存后刷新列表，便于同步更新时间/顺序
        loadNotesForBookFromServer(userName, selectedBookId);
        return currentNoteId;
    }

    async function summarizeNoteToServer(userName, noteId) {
        const params = new URLSearchParams();
        params.set('userName', String(userName));
        params.set('bookId', String(selectedBookId));
        params.set('noteId', String(noteId));
        const resp = await fetch(`${API_BASE_URL}/note/summarize`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
            body: params.toString()
        });
        const data = await resp.json();
        if (!(data && (data.code === "0" || data.code === "200"))) {
            const msg = (data && data.message) ? data.message : '生成摘要失败';
            throw new Error(msg);
        }
        let payload = data.data || {};
        if (typeof payload === 'string') {
            try { payload = JSON.parse(payload); } catch (e) { payload = {}; }
        }
        const summary = payload && payload.summary ? String(payload.summary) : '';
        return summary;
    }

    async function exportTextToDirectoryOrDownload(fileName, content) {
        const safeName = sanitizeFileName(fileName).endsWith('.txt') ? sanitizeFileName(fileName) : `${sanitizeFileName(fileName)}.txt`;
        // 优先使用目录选择（Chromium 系）
        if (window.showDirectoryPicker) {
            const dirHandle = await window.showDirectoryPicker({ mode: 'readwrite' });
            const fileHandle = await dirHandle.getFileHandle(safeName, { create: true });
            const writable = await fileHandle.createWritable();
            await writable.write(content);
            await writable.close();
            return;
        }
        // 兜底：直接下载
        const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = safeName;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }

    if (exportNoteBtn) {
        exportNoteBtn.addEventListener('click', async function() {
            try {
                const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
                if (!userName || userName === '登陆') {
                    showToast('请先登录', 'warning');
                    return;
                }
                if (!selectedBookId) {
                    showToast('请先在左侧选择一本书', 'warning');
                    return;
                }
                if (!bookshelfNotesInput) return;
                const content = (bookshelfNotesInput.value || '').trim();
                if (!content) {
                    showToast('当前笔记为空，无法导出', 'info');
                    return;
                }

                exportNoteBtn.disabled = true;
                exportNoteBtn.textContent = '…';

                const noteId = await ensureNoteSavedForExport(userName);
                if (!noteId) {
                    showToast('保存笔记失败，无法导出', 'error');
                    return;
                }

                // 先生成并落库摘要（<=200字）
                let summary = '';
                try {
                    summary = await summarizeNoteToServer(userName, noteId);
                } catch (e) {
                    // AI 未配置时允许继续导出正文
                    summary = '';
                }

                const bookName = selectedBookDisplayName || stripFileExt(selectedBookFileName) || '书籍';
                const label = (notesSelect && notesSelect.value) ? (notesSelect.options[notesSelect.selectedIndex]?.textContent || '') : '';
                const baseName = `${bookName}_${label || ('笔记' + String(noteId))}`;
                const exportBody = [
                    summary ? `【摘要】\n${formatNoteTextForExport(summary).trim()}\n` : '',
                    `【正文】\n${formatNoteTextForExport(content).trim()}\n`
                ].filter(Boolean).join('\n');

                await exportTextToDirectoryOrDownload(baseName + '.txt', exportBody);
            } catch (err) {
                const msg = err && err.message ? err.message : '导出失败';
                showToast(msg, 'error');
            } finally {
                if (exportNoteBtn) {
                    exportNoteBtn.textContent = '⤓';
                    exportNoteBtn.disabled = false;
                }
            }
        });
    }

    function appendAiMessage(role, text, isHtml) {
        if (!aiQaList) return;
        const safeRole = role === 'assistant' ? 'assistant' : 'user';
        const label = safeRole === 'assistant' ? 'AI' : '你';
        const bg = safeRole === 'assistant' ? '#f6f6f6' : '#111';
        const fg = safeRole === 'assistant' ? '#333' : '#fff';
        const wrap = document.createElement('div');
        wrap.style.borderRadius = '12px';
        wrap.style.padding = '10px 12px';
        wrap.style.background = bg;
        wrap.style.color = fg;
        wrap.style.fontSize = '13px';
        wrap.style.lineHeight = '1.6';
        const contentHtml = isHtml ? text : escapeHtml(text);
        wrap.innerHTML = `
                <div style="font-weight:700;margin-bottom:6px;">${label}</div>
            <div class="ai-msg-text">${contentHtml}</div>
        `;
        aiQaList.appendChild(wrap);
        aiQaList.scrollTop = aiQaList.scrollHeight;
        return wrap;
    }

    if (aiAskBtn) {
        aiAskBtn.addEventListener('click', function() {
            if (!aiQuestionInput) return;
            const q = (aiQuestionInput.value || '').trim();
            if (!q) return;
            const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
            if (!userName || userName === '登陆') {
                showToast('请先登录', 'warning');
                return;
            }
            if (!selectedBookId) {
                showToast('请先在左侧选择一本书', 'warning');
                return;
            }

            appendAiMessage('user', q);
            aiQuestionInput.value = '';

            const thinkingEl = appendAiMessage('assistant', '<div class="ai-msg-loading"><span class="ai-msg-loading-dot"></span><span class="ai-msg-loading-dot"></span><span class="ai-msg-loading-dot"></span></div>', true);
            aiAskBtn.disabled = true;
            aiQuestionInput.disabled = true;

            const pageNo = currentBookPageIndex || 1;
            // 用 POST 表单体，避免把大段 context 放到 URL 导致长度限制
            const form = new URLSearchParams();
            form.set('userName', String(userName));
            form.set('bookId', String(selectedBookId));
            form.set('pageNo', String(pageNo));
            form.set('question', String(q));
            if (aiSelectedContext && aiSelectedContext.trim()) {
                form.set('context', aiSelectedContext);
            }

            fetch(`${API_BASE_URL}/ai-reading/ask`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                body: form.toString()
            })
            .then(res => res.json())
            .then(data => {
                if (!(data && (data.code === "0" || data.code === "200"))) {
                    const msg = (data && data.message) ? data.message : 'AI问答失败';
                    const textEl = thinkingEl && thinkingEl.querySelector ? thinkingEl.querySelector('.ai-msg-text') : null;
                    if (textEl) textEl.innerHTML = escapeHtml(msg);
                    return;
                }
                let payload = data.data || {};
                if (typeof payload === 'string') {
                    try { payload = JSON.parse(payload); } catch (e) { payload = {}; }
                }
                const answer = payload && payload.answer ? String(payload.answer) : '（未返回内容）';
                const textEl = thinkingEl && thinkingEl.querySelector ? thinkingEl.querySelector('.ai-msg-text') : null;
                if (textEl) {
                    // 清除加载动画，使用流式显示
                    textEl.innerHTML = '';
                    typeWriter(textEl, answer, 15);
                }
            })
            .catch(() => {
                const textEl = thinkingEl && thinkingEl.querySelector ? thinkingEl.querySelector('.ai-msg-text') : null;
                if (textEl) textEl.innerHTML = escapeHtml('AI问答失败：网络错误或后端未启动');
            })
            .finally(() => {
                aiAskBtn.disabled = false;
                aiQuestionInput.disabled = false;
                aiQuestionInput.focus();
            });
        });
    }

    // Enter 直接提问；Shift+Enter 换行
    if (aiQuestionInput) {
        aiQuestionInput.addEventListener('keydown', function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                if (aiAskBtn && !aiAskBtn.disabled) {
                    aiAskBtn.click();
                }
            }
        });
    }

    // ========== 选中文本 -> 添加为 AI 上下文 ==========
    function getSelectionTextIn(el) {
        if (!el) return '';
        const sel = window.getSelection ? window.getSelection() : null;
        if (!sel || sel.rangeCount === 0) return '';
        const text = String(sel.toString() || '').trim();
        if (!text) return '';
        const range = sel.getRangeAt(0);
        const node = range && range.commonAncestorContainer ? range.commonAncestorContainer : null;
        // 仅当选区发生在 readerContent 内部才触发
        if (node && el.contains(node.nodeType === 1 ? node : node.parentNode)) {
            return text;
        }
        return '';
    }

    function maybeAddSelectionAsContext() {
        if (!selectedBookId) return; // 未选书不触发
        const text = getSelectionTextIn(readerContent);
        if (!text) return;
        // 直接添加为右侧问答上下文（不弹窗）
        // 过短的选区容易误触，这里做一个轻微阈值
        if (text.length < 2) return;
        if (aiSelectedContext && aiSelectedContext.trim() === text.trim()) return;
        setAiSelectedContext(text, currentBookPageIndex || 0);
        // 不清除选区，方便用户复制（Ctrl+C）后再点其他地方取消高亮即可）
    }

    if (readerContent) {
        readerContent.addEventListener('mouseup', function() {
            // mouseup 立即读 selection 有时不稳定，延迟一帧
            setTimeout(maybeAddSelectionAsContext, 0);
        });
        readerContent.addEventListener('keyup', function(e) {
            // 键盘选择（Shift+方向键等），这里不做复杂判断，统一延迟检查一次
            setTimeout(maybeAddSelectionAsContext, 0);
        });
    }

    if (aiContextClearBtn) {
        aiContextClearBtn.addEventListener('click', function() {
            clearAiSelectedContext();
        });
    }

    // ========== 选中 AI 回答文本 -> 非弹窗提示是否加入笔记 ==========
    function getSelectionTextInAi() {
        return getSelectionTextIn(aiQaList);
    }

    function maybeShowAiSelectionBar() {
        if (!selectedBookId) return; // 笔记与书绑定
        const text = getSelectionTextInAi();
        if (!text) return;
        if (text.length < 2) return;
        showAiSelectionBar(text);
    }

    if (aiQaList) {
        aiQaList.addEventListener('mouseup', function() {
            setTimeout(maybeShowAiSelectionBar, 0);
        });
        aiQaList.addEventListener('keyup', function() {
            setTimeout(maybeShowAiSelectionBar, 0);
        });
    }

    if (dismissAiSelectionBarBtn) {
        dismissAiSelectionBarBtn.addEventListener('click', function() {
            hideAiSelectionBar();
        });
    }

    if (addAiSelectionToNotesBtn) {
        addAiSelectionToNotesBtn.addEventListener('click', function() {
            const t = String(lastAiSelectionText || '').trim();
            if (!t) {
                hideAiSelectionBar();
                return;
            }
            if (!bookshelfNotesInput) return;
            const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
            if (!userName || userName === '登陆') {
                showToast('请先登录', 'warning');
                return;
            }
            if (!selectedBookId) {
                showToast('请先选择书籍', 'warning');
                return;
            }
            const tag = '[AI摘录]';
            const before = bookshelfNotesInput.value || '';
            const sep = before.trim() ? '\n\n' : '';
            bookshelfNotesInput.value = before + sep + tag + '\n' + t;
            scheduleSaveNotes(userName, selectedBookId);
            if (bookshelfNotesStatus) {
                bookshelfNotesStatus.textContent = `已添加AI摘录 ${formatTimeHHMMSS(new Date())}`;
            }
            hideAiSelectionBar();
            // 清掉高亮选区（避免一直蓝着影响阅读）
            try {
                const sel = window.getSelection ? window.getSelection() : null;
                if (sel) sel.removeAllRanges();
            } catch (_) {}
        });
    }

    if (closeBookReaderModalBtn) {
        closeBookReaderModalBtn.addEventListener('click', closeBookReaderModal);
    }
    if (bookReaderModal) {
        bookReaderModal.addEventListener('click', function(e) {
            if (e.target === bookReaderModal) {
                closeBookReaderModal();
            }
        });
    }
    if (bookPrevPageBtn) {
        bookPrevPageBtn.addEventListener('click', function() {
            currentBookPageIndex = Math.max(0, currentBookPageIndex - 1);
            renderCurrentBookPage();
        });
    }
    if (bookNextPageBtn) {
        bookNextPageBtn.addEventListener('click', function() {
            currentBookPageIndex = Math.min(Math.max(0, currentBookPages.length - 1), currentBookPageIndex + 1);
            renderCurrentBookPage();
        });
    }

    if (bookshelfSearchInput) {
        bookshelfSearchInput.addEventListener('input', function() {
            applyBookshelfFilterAndRender();
        });
    }

    if (aiUploadForm) {
        aiUploadForm.addEventListener('submit', function(e) {
            e.preventDefault();

            if (!loginBtn.classList.contains('logged-in')) {
                showToast('请先登录以使用AI阅读功能', 'warning');
                return;
            }

            const file = aiReadingFileInput && aiReadingFileInput.files[0];
            if (!file) {
                showUploadMessage('请先选择文件', false);
                return;
            }

            // 仅允许 txt/doc/docx/pdf
            const allowedExt = ['txt', 'doc', 'docx', 'pdf', 'png', 'jpg', 'jpeg'];
            const fileName = (file.name || '').toLowerCase();
            const dotIndex = fileName.lastIndexOf('.');
            const ext = dotIndex >= 0 ? fileName.substring(dotIndex + 1) : '';
            if (!allowedExt.includes(ext)) {
                showUploadMessage('仅支持上传 txt、doc、docx、pdf、png、jpg 格式文件', false);
                // 清空选择，避免用户误以为已选择成功
                if (aiReadingFileInput) {
                    aiReadingFileInput.value = '';
                }
                return;
            }

            // 选取文件时已做过预校验；这里再挡一次，避免用户绕过UI
            if (duplicateBlocked) {
                showUploadMessage('该文件名已存在，请修改文件名后再上传', false);
                return;
            }

            const uploadBtn = aiUploadForm.querySelector('.ai-reading-upload-btn');
            const originalText = uploadBtn ? uploadBtn.textContent : '';

            if (uploadBtn) {
                uploadBtn.textContent = '上传中...';
                uploadBtn.disabled = true;
            }

            // 隐藏之前的消息，显示进度条
            showUploadMessage('', true);
            showUploadProgress();
            updateUploadProgress(0, '开始上传...');

            const formData = new FormData();
            formData.append('file', file);
            // 获取当前登录的用户名
            const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
            formData.append('userName', userName);

            // 使用XMLHttpRequest来跟踪上传进度
            const xhr = new XMLHttpRequest();

            // 监听上传进度
            xhr.upload.addEventListener('progress', function(e) {
                if (e.lengthComputable) {
                    const percentComplete = (e.loaded / e.total) * 100;
                    updateUploadProgress(percentComplete, '上传中... ' + formatFileSize(e.loaded) + ' / ' + formatFileSize(e.total));
                }
            });

            // 上传完成
            xhr.addEventListener('load', function() {
                if (xhr.status >= 200 && xhr.status < 300) {
                    try {
                        const data = JSON.parse(xhr.responseText);
                        const responseData = data || {};
                        const isSuccess = responseData.code === "0" || responseData.code === "200" || responseData.success === true || Object.keys(responseData).length === 0;
                        const message = responseData.message || (isSuccess ? '上传成功' : '上传失败，请稍后重试');
                        
                        if (isSuccess) {
                            updateUploadProgress(100, '上传完成');
                            // 延迟一下再隐藏进度条，让用户看到完成状态
                            setTimeout(() => {
                                hideUploadProgress();
                                showUploadMessage(message, true);
                                aiUploadForm.reset();
                                // 恢复按钮状态
                                if (uploadBtn) {
                                    uploadBtn.textContent = originalText || '上传';
                                    uploadBtn.disabled = false;
                                }
                                
                                // Start polling for parsing progress
                                // Use file.name or file.originalFilename
                                startParsingListPolling(userName);
                            }, 500);
                        } else {
                            hideUploadProgress();
                            showUploadMessage(message, false);
                            // 恢复按钮状态
                            if (uploadBtn) {
                                uploadBtn.textContent = originalText || '上传';
                                uploadBtn.disabled = false;
                            }
                        }
                    } catch (e) {
                        updateUploadProgress(100, '上传完成');
                        setTimeout(() => {
                            hideUploadProgress();
                            showUploadMessage('上传成功', true);
                            aiUploadForm.reset();
                            // 恢复按钮状态
                            if (uploadBtn) {
                                uploadBtn.textContent = originalText || '上传';
                                uploadBtn.disabled = false;
                            }
                        }, 500);
                    }
                } else {
                    hideUploadProgress();
                    showUploadMessage(`上传失败: HTTP ${xhr.status}`, false);
                    // 恢复按钮状态
                    if (uploadBtn) {
                        uploadBtn.textContent = originalText || '上传';
                        uploadBtn.disabled = false;
                    }
                }
            });

            // 上传错误
            xhr.addEventListener('error', function() {
                hideUploadProgress();
                showUploadMessage('上传失败，网络错误', false);
                // 恢复按钮状态
                if (uploadBtn) {
                    uploadBtn.textContent = originalText || '上传';
                    uploadBtn.disabled = false;
                }
            });

            // 上传中止
            xhr.addEventListener('abort', function() {
                hideUploadProgress();
                showUploadMessage('上传已取消', false);
                // 恢复按钮状态
                if (uploadBtn) {
                    uploadBtn.textContent = originalText || '上传';
                    uploadBtn.disabled = false;
                }
            });

            // 发送请求
            xhr.open('POST', `${API_BASE_URL}/ai-reading/upload`);
            xhr.send(formData);
        });
    }

    // 选取文件时就做同名校验（更早反馈）
    if (aiReadingFileInput) {
        aiReadingFileInput.addEventListener('change', function() {
            duplicateBlocked = false;
            setUploadButtonDisabled(false);

            if (!loginBtn.classList.contains('logged-in')) return;
            const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
            const file = aiReadingFileInput.files && aiReadingFileInput.files[0];
            if (!userName || !file) return;
            checkDuplicateOnSelect(file, userName);
        });
    }

    // Start polling parsing progress list (stacked)
    function startParsingListPolling(userName) {
        showParsingProgress();
        resetParsingProgress();

        // Clear any existing interval
        if (parsingInterval) {
            clearInterval(parsingInterval);
        }

        const poll = () => {
            fetch(`${API_BASE_URL}/ai-reading/check-processing-list?userName=${encodeURIComponent(userName)}`, {
                method: 'POST'
            })
            .then(response => response.json())
            .then(data => {
                if (data.code === "0" || data.code === "200") {
                    let list = data.data || [];
                    if (typeof list === 'string') {
                        try {
                            list = JSON.parse(list);
                        } catch (e) {
                            list = [];
                        }
                    }

                    if (!Array.isArray(list) || list.length === 0) {
                        hideParsingProgress();
                        // 解析队列为空时刷新书架（展示已完成的书）
                        loadBookshelf(userName);
                        return;
                    }

                    renderParsingProgressList(list);
                }
            })
            .catch(error => {
                // Ignore polling errors
            });
        };

        // Execute immediately
        poll();

        // Then start interval
        parsingInterval = setInterval(poll, 5000);
    }

    // 格式化文件大小
    function formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
    }

    // 打开登录弹窗或登出
    loginBtn.addEventListener('click', function(e) {
        e.preventDefault();
        
        // 检查是否已登录（通过按钮文字判断）
        if (loginBtn.textContent !== '登陆') {
            // 已登录状态，执行登出
            if (confirm('确定要登出吗？')) {
                logout();
            }
        } else {
            // 未登录状态，打开登录弹窗
            loginModal.classList.add('show');
            document.body.style.overflow = 'hidden';
        }
    });

    // 登出功能
    function logout() {
        // 清除本地存储
        localStorage.removeItem('rememberedUser');
        
        // 恢复登录按钮状态
        loginBtn.textContent = '登陆';
        loginBtn.classList.remove('logged-in');
        hideAiReadingTab();
        switchToHome();
        
        // 可以在这里调用登出API
        fetch(`${API_BASE_URL}/logout`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            }
        }).catch(error => {
            // Ignore logout API errors
        });
        
        showToast('已登出', 'success');
    }

    // 关闭弹窗的通用函数
    function closeModal(modal) {
        modal.classList.remove('show');
        document.body.style.overflow = 'auto'; // 恢复滚动
    }

    // 点击关闭按钮
    closeLoginBtn.addEventListener('click', function() {
        closeModal(loginModal);
    });

    closeRegisterBtn.addEventListener('click', function() {
        closeModal(registerModal);
    });

    // 点击遮罩层关闭弹窗
    loginModal.addEventListener('click', function(e) {
        if (e.target === loginModal) {
            closeModal(loginModal);
        }
    });

    registerModal.addEventListener('click', function(e) {
        if (e.target === registerModal) {
            closeModal(registerModal);
        }
    });

    // 按ESC键关闭弹窗；阅读器内左右键翻页
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            if (loginModal.classList.contains('show')) {
                closeModal(loginModal);
            } else if (registerModal.classList.contains('show')) {
                closeModal(registerModal);
            } else if (termsModal.classList.contains('show')) {
                closeModal(termsModal);
            } else if (bookReaderModal && bookReaderModal.classList.contains('show')) {
                closeBookReaderModal();
            }
            return;
        }
        // 阅读器内左右键翻页（输入框/文本域内不响应，避免影响输入）
        const active = document.activeElement;
        const isInput = active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA');
        if (isInput || (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight')) return;

        // 情况一：书架内嵌阅读器（书架页可见且已选书）
        const bookshelfReaderActive = bookshelfSection && !bookshelfSection.classList.contains('hidden') && selectedBookId;
        if (bookshelfReaderActive) {
            e.preventDefault();
            const userName = loginBtn.textContent || localStorage.getItem('rememberedUser') || '';
            if (!userName || userName === '登陆') return;
            const total = selectedBookTotalPages || 0;
            const current = currentBookPageIndex || 1;
            if (e.key === 'ArrowLeft') {
                const target = Math.max(1, current - 1);
                if (target !== current) loadWorkspaceReaderPage(userName, selectedBookId, target);
            } else {
                const target = total > 0 ? Math.min(total, current + 1) : current + 1;
                if (target !== current) loadWorkspaceReaderPage(userName, selectedBookId, target);
            }
            return;
        }

        // 情况二：弹窗阅读器（bookReaderModal 打开且已有页码数据）
        if (bookReaderModal && bookReaderModal.classList.contains('show') && currentBookPages && currentBookPages.length > 0) {
            e.preventDefault();
            const total = currentBookPages.length;
            const idx = currentBookPageIndex || 0;
            if (e.key === 'ArrowLeft') {
                currentBookPageIndex = Math.max(0, idx - 1);
                renderCurrentBookPage();
            } else {
                currentBookPageIndex = Math.min(total - 1, idx + 1);
                renderCurrentBookPage();
            }
        }
    });

    // 处理登录表单提交
    loginForm.addEventListener('submit', function(e) {
        e.preventDefault();
        
        const username = document.getElementById('loginUsername').value;
        const password = document.getElementById('loginPassword').value;
        const remember = document.querySelector('input[name="remember"]').checked;

        // 简单的表单验证
        if (!username.trim()) {
            showToast('请输入用户名', 'warning');
            return;
        }
        
        if (!password.trim()) {
            showToast('请输入密码', 'warning');
            return;
        }

        // 登录过程
        const submitBtn = document.querySelector('.login-submit-btn');
        const originalText = submitBtn.textContent;
        
        submitBtn.textContent = '登录中...';
        submitBtn.disabled = true;

        // 调用登录API
        fetch(`${API_BASE_URL}/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                userName: username,
                password: password,
                remember: remember
            })
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            // 根据返回的code判断成功或失败
            if (data.code === "0") {
                // 登录成功
                showToast(data.message || '登录成功！', 'success');
                closeModal(loginModal);
                
                // 重置表单
                loginForm.reset();
                submitBtn.textContent = originalText;
                submitBtn.disabled = false;
                
                // 更新UI，显示用户名
                loginBtn.textContent = username;
                loginBtn.classList.add('logged-in');
                showAiReadingTab();
                showBookshelfTab();
                
                // 保存登录状态到localStorage
                if (remember) {
                    localStorage.setItem('rememberedUser', username);
                }
            } else {
                // 登录失败，显示后端返回的错误信息
                showToast(data.message || '登录失败，请重试', 'error');
                submitBtn.textContent = originalText;
                submitBtn.disabled = false;
            }
        })
        .catch(error => {
            // 登录失败
            showToast('登录失败，请检查用户名和密码', 'error');
            
            // 恢复按钮状态
            submitBtn.textContent = originalText;
            submitBtn.disabled = false;
        });
    });

    // 忘记密码链接
    document.querySelector('.forgot-password').addEventListener('click', function(e) {
        e.preventDefault();
        showToast('忘记密码功能暂未实现', 'info');
    });

    // 注册链接 - 从登录弹窗切换到注册弹窗
    document.querySelector('.register-link').addEventListener('click', function(e) {
        e.preventDefault();
        closeModal(loginModal);
        registerModal.classList.add('show');
        document.body.style.overflow = 'hidden';
    });

    // 登录链接 - 从注册弹窗切换到登录弹窗
    document.querySelector('.login-link').addEventListener('click', function(e) {
        e.preventDefault();
        closeModal(registerModal);
        loginModal.classList.add('show');
        document.body.style.overflow = 'hidden';
    });

    // 处理注册表单提交
    registerForm.addEventListener('submit', function(e) {
        e.preventDefault();
        
        const username = document.getElementById('registerUsername').value;
        const email = document.getElementById('registerEmail').value;
        const password = document.getElementById('registerPassword').value;
        const confirmPassword = document.getElementById('confirmPassword').value;
        const agreeTerms = document.querySelector('input[name="agreeTerms"]').checked;

        // 清除所有之前的错误提示
        removeAllErrors();
        
        // 表单验证
        let hasError = false;
        
        if (!username.trim()) {
            showFieldError('registerUsername', '请输入用户名');
            hasError = true;
        }
        
        if (!email.trim()) {
            showFieldError('registerEmail', '请输入邮箱');
            hasError = true;
        } else {
            // 邮箱格式验证
            const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
            if (!emailRegex.test(email)) {
                showFieldError('registerEmail', '请输入正确的邮箱格式');
                hasError = true;
            }
        }
        
        if (!password.trim()) {
            showFieldError('registerPassword', '请输入密码');
            hasError = true;
        } else if (password.length < 6) {
            showFieldError('registerPassword', '密码长度至少6位');
            hasError = true;
        }
        
        if (!confirmPassword.trim()) {
            showFieldError('confirmPassword', '请再次输入密码');
            hasError = true;
        } else if (password !== confirmPassword) {
            showPasswordError('两次输入的密码不一致');
            hasError = true;
        }
        
        if (!agreeTerms) {
            showToast('请同意用户协议', 'warning');
            return;
        }
        
        if (hasError) {
            return;
        }

        // 注册过程
        const submitBtn = document.querySelector('.register-submit-btn');
        const originalText = submitBtn.textContent;
        
        submitBtn.textContent = '注册中...';
        submitBtn.disabled = true;

        // 调用注册API
        fetch(`${API_BASE_URL}/register`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                userName: username,
                email: email,
                password: password
            })
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            // 根据返回的code判断成功或失败
            if (data.code === "0") {
                // 注册成功
                showToast(data.message || '注册成功！请登录', 'success');
                closeModal(registerModal);
                
                // 重置表单
                registerForm.reset();
                submitBtn.textContent = originalText;
                submitBtn.disabled = false;
                
                // 自动打开登录弹窗
                setTimeout(() => {
                    loginModal.classList.add('show');
                    document.body.style.overflow = 'hidden';
                }, 500);
            } else {
                // 注册失败，显示后端返回的错误信息
                showToast(data.message || '注册失败，请重试', 'error');
                submitBtn.textContent = originalText;
                submitBtn.disabled = false;
            }
        })
        .catch(error => {
            // 根据错误类型显示不同的提示
            let errorMessage = '注册失败，请检查网络连接或稍后重试';
            
            if (error.message.includes('Failed to fetch')) {
                errorMessage = '无法连接到服务器，请检查后端服务是否启动';
            } else if (error.message.includes('404')) {
                errorMessage = '注册接口不存在，请检查后端API';
            } else if (error.message.includes('500')) {
                errorMessage = '服务器内部错误，请稍后重试';
            }
            
            showToast(errorMessage, 'error');
            
            // 恢复按钮状态
            submitBtn.textContent = originalText;
            submitBtn.disabled = false;
        });
    });

    // 用户协议弹窗功能
    const termsModal = document.getElementById('termsModal');
    const showTermsBtn = document.getElementById('showTerms');
    const closeTermsBtn = document.getElementById('closeTermsModal');

    // 显示用户协议弹窗
    showTermsBtn.addEventListener('click', function(e) {
        e.preventDefault();
        termsModal.classList.add('show');
        document.body.style.overflow = 'hidden';
    });

    // 关闭用户协议弹窗
    closeTermsBtn.addEventListener('click', function() {
        closeModal(termsModal);
    });

    // 点击遮罩层关闭用户协议弹窗
    termsModal.addEventListener('click', function(e) {
        if (e.target === termsModal) {
            closeModal(termsModal);
        }
    });

    // 邮箱输入框失去焦点时的验证
    const emailInput = document.getElementById('registerEmail');
    
    // 用户重新输入时清除错误提示
    emailInput.addEventListener('input', function() {
        removeEmailError();
        this.style.borderColor = '#ddd';
    });
    
    emailInput.addEventListener('blur', function() {
        const email = this.value.trim();
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        
        // 移除之前的错误提示
        removeEmailError();
        
        if (email && !emailRegex.test(email)) {
            this.style.borderColor = '#e74c3c';
            showEmailError('请输入正确的邮箱格式，如：12345678@qq.com');
        } else if (email && emailRegex.test(email)) {
            this.style.borderColor = '#27ae60';
        } else {
            this.style.borderColor = '#ddd';
        }
    });

    // 显示邮箱错误提示
    function showEmailError(message) {
        const emailGroup = emailInput.parentElement;
        const errorDiv = document.createElement('div');
        errorDiv.className = 'email-error-message';
        errorDiv.textContent = message;
        errorDiv.style.color = '#e74c3c';
        errorDiv.style.fontSize = '12px';
        errorDiv.style.marginTop = '4px';
        emailGroup.appendChild(errorDiv);
    }

    // 移除邮箱错误提示
    function removeEmailError() {
        const existingError = document.querySelector('.email-error-message');
        if (existingError) {
            existingError.remove();
        }
    }

    // 密码显示/隐藏功能
    function initPasswordToggle(passwordInputId, toggleId) {
        const passwordInput = document.getElementById(passwordInputId);
        const toggle = document.getElementById(toggleId);
        
        toggle.addEventListener('click', function() {
            if (passwordInput.type === 'password') {
                passwordInput.type = 'text';
                toggle.classList.add('active');
            } else {
                passwordInput.type = 'password';
                toggle.classList.remove('active');
            }
        });
    }

    // 初始化所有密码输入框的显示/隐藏功能
    initPasswordToggle('loginPassword', 'loginPasswordToggle');
    initPasswordToggle('registerPassword', 'registerPasswordToggle');
    initPasswordToggle('confirmPassword', 'confirmPasswordToggle');

    // 页面加载时检查登录状态
    function checkLoginStatus() {
        const rememberedUser = localStorage.getItem('rememberedUser');
        if (rememberedUser) {
            loginBtn.textContent = rememberedUser;
            loginBtn.classList.add('logged-in');
            showAiReadingTab();
            showBookshelfTab();
        } else {
            hideAiReadingTab();
        }
    }

    // 初始化登录状态检查
    checkLoginStatus();
    
    // 密码一致性验证
    function initPasswordValidation() {
        const passwordInput = document.getElementById('registerPassword');
        const confirmPasswordInput = document.getElementById('confirmPassword');
        
        // 确认密码输入时验证
        confirmPasswordInput.addEventListener('input', function() {
            const password = passwordInput.value;
            const confirmPassword = this.value;
            
            // 移除之前的错误提示
            removePasswordError();
            
            if (confirmPassword && password !== confirmPassword) {
                this.style.borderColor = '#e74c3c';
                showPasswordError('两次输入的密码不一致');
            } else if (confirmPassword && password === confirmPassword) {
                this.style.borderColor = '#27ae60';
                passwordInput.style.borderColor = '#27ae60';
            } else {
                this.style.borderColor = '#ddd';
                passwordInput.style.borderColor = '#ddd';
            }
        });
        
        // 密码输入时也验证
        passwordInput.addEventListener('input', function() {
            const password = this.value;
            const confirmPassword = confirmPasswordInput.value;
            
            if (confirmPassword) {
                if (password !== confirmPassword) {
                    this.style.borderColor = '#e74c3c';
                    confirmPasswordInput.style.borderColor = '#e74c3c';
                    showPasswordError('两次输入的密码不一致');
                } else {
                    this.style.borderColor = '#27ae60';
                    confirmPasswordInput.style.borderColor = '#27ae60';
                    removePasswordError();
                }
            } else {
                this.style.borderColor = '#ddd';
            }
        });
    }
    
    // 显示密码错误提示
    function showPasswordError(message) {
        const confirmPasswordGroup = document.getElementById('confirmPassword').parentElement.parentElement; // 获取form-group
        const errorDiv = document.createElement('div');
        errorDiv.className = 'password-error-message';
        errorDiv.textContent = message;
        errorDiv.style.color = '#e74c3c';
        errorDiv.style.fontSize = '12px';
        errorDiv.style.marginTop = '4px';
        errorDiv.style.fontWeight = '500';
        errorDiv.style.display = 'block';
        errorDiv.style.width = '100%';
        // 在form-group下方添加错误提示
        confirmPasswordGroup.appendChild(errorDiv);
    }
    
    // 移除密码错误提示
    function removePasswordError() {
        const existingError = document.querySelector('.password-error-message');
        if (existingError) {
            existingError.remove();
        }
    }
    
    // 通用错误提示函数
    function showFieldError(fieldId, message) {
        const fieldGroup = document.getElementById(fieldId).parentElement;
        const errorDiv = document.createElement('div');
        errorDiv.className = 'field-error-message';
        errorDiv.textContent = message;
        errorDiv.style.color = '#e74c3c';
        errorDiv.style.fontSize = '12px';
        errorDiv.style.marginBottom = '8px';
        errorDiv.style.fontWeight = '500';
        // 在输入框上方插入错误提示
        fieldGroup.insertBefore(errorDiv, fieldGroup.firstChild);
    }
    
    // 移除通用错误提示
    function removeFieldError(fieldId) {
        const fieldGroup = document.getElementById(fieldId).parentElement;
        const existingError = fieldGroup.querySelector('.field-error-message');
        if (existingError) {
            existingError.remove();
        }
    }
    
    // 移除所有错误提示
    function removeAllErrors() {
        const allErrors = document.querySelectorAll('.password-error-message, .field-error-message');
        allErrors.forEach(error => error.remove());
    }
    
    // 初始化密码验证
    initPasswordValidation();
    
    // 添加输入框焦点事件，清除错误提示
    function initFieldFocusEvents() {
        const fields = ['registerUsername', 'registerEmail', 'registerPassword', 'confirmPassword'];
        
        fields.forEach(fieldId => {
            const field = document.getElementById(fieldId);
            if (field) {
                field.addEventListener('focus', function() {
                    removeFieldError(fieldId);
                    // 如果是密码相关字段，也清除密码错误提示
                    if (fieldId === 'registerPassword' || fieldId === 'confirmPassword') {
                        removePasswordError();
                    }
                });
            }
        });
    }
    
    // 初始化字段焦点事件
    initFieldFocusEvents();
});

// 文章列表功能
document.addEventListener('DOMContentLoaded', function() {
    // 使用同源地址，避免部署到服务器后仍请求客户端本机 127.0.0.1
    const API_BASE_URL = window.location.origin;
    const articleList = document.getElementById('articleList');
    const loadingIndicator = document.getElementById('loadingIndicator');
    const paginationContainer = document.getElementById('paginationContainer');
    const pageNumbers = document.getElementById('pageNumbers');
    
    // 当前分页状态
    let currentPage = 1;
    const pageSize = 5; // 每页显示5篇文章
    let totalPages = 1;
    
    // 初始化：先获取文章总数，再加载第一页文章
    initArticleList();
    
    // 初始化文章列表
    async function initArticleList() {
        try {
            // 先获取文章总数
            await loadArticleCount();
            // 再加载第一页文章
            loadArticles(currentPage);
        } catch (error) {
            showError('初始化失败，请刷新页面重试');
        }
    }
    
    // 获取文章总数
    function loadArticleCount() {
        return fetch(`${API_BASE_URL}/article/count`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({})
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            // 检查响应码
            if (data.code === "0" || data.code === "200") {
                // 解析返回的数据
                let count = 0;
                if (data.data) {
                    try {
                        count = typeof data.data === 'string' 
                            ? parseInt(data.data) 
                            : data.data;
                    } catch (e) {
                        count = 0;
                    }
                }
                
                // 计算总页数
                totalPages = Math.max(1, Math.ceil(count / pageSize));
            } else {
                throw new Error(data.message || '获取文章总数失败');
            }
        })
        .catch(error => {
            // 如果获取总数失败，仍然尝试加载文章，使用原来的逻辑
            totalPages = 1;
        });
    }
    
    // 加载文章列表
    function loadArticles(pageNum) {
        // 显示加载状态
        showLoading();
        
        // 发送请求获取文章列表
        fetch(`${API_BASE_URL}/article/list`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                pageNum: pageNum,
                pageSize: pageSize
            })
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            // 检查响应码（接口返回"0"表示成功，"200"也表示成功）
            if (data.code === "0" || data.code === "200") {
                // 解析返回的数据
                let articles = [];
                if (data.data) {
                    try {
                        // data字段是字符串形式的JSON，需要解析
                        articles = typeof data.data === 'string' 
                            ? JSON.parse(data.data) 
                            : data.data;
                    } catch (e) {
                        articles = [];
                    }
                }
                
                // 渲染文章列表
                renderArticles(articles);
                
                // 更新分页控件（总页数已经在初始化时通过总数接口获取）
                updatePagination();
            } else {
                throw new Error(data.message || '获取文章列表失败');
            }
        })
        .catch(error => {
            showError(error.message || '加载文章失败，请稍后重试');
        });
    }
    
    // 渲染文章列表
    function renderArticles(articles) {
        // 清空现有文章内容（但保留loadingIndicator的结构）
        // 移除所有文章项、错误消息和空状态
        const itemsToRemove = articleList.querySelectorAll('.article-item, .error-message, .empty-state');
        itemsToRemove.forEach(item => item.remove());
        
        // 隐藏加载状态
        hideLoading();
        
        // 如果没有文章，显示空状态
        if (!articles || articles.length === 0) {
            showEmptyState();
            return;
        }
        
        // 创建文章HTML
        articles.forEach((article, index) => {
            const articleElement = createArticleElement(article);
            articleList.appendChild(articleElement);
        });
    }
    
    // 创建单篇文章HTML
    function createArticleElement(article) {
        const articleItem = document.createElement('article');
        articleItem.className = 'article-item';
        
        // 格式化日期
        const formattedDate = formatDate(article.createTime || article.updateTime);
        
        // 构建HTML
        articleItem.innerHTML = `
            <div class="article-image">
                <img src="${article.cover || 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&h=200&fit=crop'}" 
                     alt="${article.title || '文章'}">
            </div>
            <div class="article-content">
                <div class="article-header">
                    <h3 class="article-title">${article.title || '无标题'}</h3>
                    <span class="article-date">${formattedDate}</span>
                </div>
                <p class="article-excerpt">
                    ${article.summary || article.content || '暂无摘要'}
                </p>
                <button class="read-more-btn" data-article-id="${article.id || ''}">Read More</button>
            </div>
        `;
        
        // 为Read More按钮添加点击事件
        const readMoreBtn = articleItem.querySelector('.read-more-btn');
        readMoreBtn.addEventListener('click', function() {
            showArticleDetail(article);
        });
        
        return articleItem;
    }
    
    // 格式化日期
    function formatDate(dateString) {
        if (!dateString) return '';
        
        try {
            const date = new Date(dateString);
            return date.toLocaleDateString('zh-CN', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit'
            });
        } catch (e) {
            return dateString;
        }
    }
    
    // 显示加载状态
    function showLoading() {
        loadingIndicator.classList.remove('hidden');
        paginationContainer.style.display = 'none';
    }
    
    // 隐藏加载状态
    function hideLoading() {
        loadingIndicator.classList.add('hidden');
        paginationContainer.style.display = 'flex';
    }
    
    // 显示错误状态
    function showError(message) {
        hideLoading();
        
        // 移除现有的错误消息
        const existingError = articleList.querySelector('.error-message');
        if (existingError) {
            existingError.remove();
        }
        
        // 创建错误消息
        const errorDiv = document.createElement('div');
        errorDiv.className = 'error-message';
        errorDiv.innerHTML = `
            <p>${message}</p>
            <button class="error-retry-btn" onclick="location.reload()">重试</button>
        `;
        
        articleList.appendChild(errorDiv);
    }
    
    // 显示空状态
    function showEmptyState() {
        const emptyDiv = document.createElement('div');
        emptyDiv.className = 'empty-state';
        emptyDiv.innerHTML = '<p>暂无文章</p>';
        articleList.appendChild(emptyDiv);
        
        paginationContainer.style.display = 'none';
    }
    
    // 更新分页控件
    function updatePagination() {
        // 清空页码按钮
        pageNumbers.innerHTML = '';
        
        // 生成所有页码按钮（1, 2, 3...格式）
        for (let i = 1; i <= totalPages; i++) {
            const pageBtn = document.createElement('button');
            pageBtn.className = `page-btn page-number ${i === currentPage ? 'active' : ''}`;
            pageBtn.textContent = i;
            pageBtn.addEventListener('click', () => {
                currentPage = i;
                loadArticles(currentPage);
                // 滚动到顶部
                window.scrollTo({ top: 0, behavior: 'smooth' });
            });
            pageNumbers.appendChild(pageBtn);
        }
    }
});

// 文章详情功能
document.addEventListener('DOMContentLoaded', function() {
    // 使用同源地址，避免部署到服务器后仍请求客户端本机 127.0.0.1
    const API_BASE_URL = window.location.origin;
    const articleDetailModal = document.getElementById('articleDetailModal');
    const closeArticleDetailBtn = document.getElementById('closeArticleDetailModal');
    const articleDetailTitle = document.getElementById('articleDetailTitle');
    const articleDetailContent = document.getElementById('articleDetailContent');
    
    // 显示文章详情
    window.showArticleDetail = function(article) {
        // 设置标题
        articleDetailTitle.textContent = article.title || '文章详情';
        
        // 显示加载状态
        articleDetailContent.innerHTML = `
            <div class="article-detail-loading">
                <div class="loading-spinner"></div>
                <p>加载中...</p>
            </div>
        `;
        
        // 打开模态框
        articleDetailModal.classList.add('show');
        document.body.style.overflow = 'hidden';
        
        // 如果文章有完整内容，直接显示
        if (article.content) {
            renderArticleDetail(article);
        } else if (article.id) {
            // 如果有ID，尝试从API获取详细内容
            loadArticleDetail(article.id);
        } else {
            // 如果没有内容也没有ID，显示可用信息
            renderArticleDetail(article);
        }
    };
    
    // 从API加载文章详情
    function loadArticleDetail(articleId) {
        // 尝试调用文章详情API（如果后端支持）
        fetch(`${API_BASE_URL}/article/detail`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                id: articleId
            })
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            if (data.code === "0" || data.code === "200") {
                let articleData = null;
                if (data.data) {
                    try {
                        articleData = typeof data.data === 'string' 
                            ? JSON.parse(data.data) 
                            : data.data;
                    } catch (e) {
                        // Ignore parse errors
                    }
                }
                if (articleData) {
                    renderArticleDetail(articleData);
                } else {
                    throw new Error('无法获取文章详情');
                }
            } else {
                throw new Error(data.message || '获取文章详情失败');
            }
        })
        .catch(error => {
            // 如果API调用失败，尝试使用列表中的信息
            articleDetailContent.innerHTML = `
                <div class="article-detail-error">
                    <p>无法加载文章详情，请稍后重试</p>
                </div>
            `;
        });
    }
    
    // 渲染文章详情
    function renderArticleDetail(article) {
        const formattedDate = formatArticleDate(article.createTime || article.updateTime);
        
        articleDetailContent.innerHTML = `
            <div class="article-detail-header">
                <div class="article-detail-meta">
                    <span class="article-detail-date">${formattedDate}</span>
                    ${article.author ? `<span class="article-detail-author">作者：${article.author}</span>` : ''}
                    ${article.category ? `<span class="article-detail-category">分类：${article.category}</span>` : ''}
                </div>
                ${article.cover ? `<div class="article-detail-cover">
                    <img src="${article.cover}" alt="${article.title || '文章封面'}">
                </div>` : ''}
            </div>
            <div class="article-detail-text">
                ${formatArticleContent(article.content || article.summary || '暂无内容')}
            </div>
        `;
    }
    
    // 格式化文章日期
    function formatArticleDate(dateString) {
        if (!dateString) return '';
        
        try {
            const date = new Date(dateString);
            return date.toLocaleDateString('zh-CN', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (e) {
            return dateString;
        }
    }
    
    // 格式化文章内容（支持换行）
    function formatArticleContent(content) {
        if (!content) return '<p>暂无内容</p>';
        
        // 将换行符转换为<br>标签
        const formatted = content
            .replace(/\n/g, '<br>')
            .replace(/\r\n/g, '<br>');
        
        // 如果内容没有HTML标签，则包装在<p>标签中
        if (!formatted.includes('<') && !formatted.includes('>')) {
            return `<p>${formatted}</p>`;
        }
        
        return formatted;
    }
    
    // 关闭文章详情模态框
    function closeArticleDetailModal() {
        articleDetailModal.classList.remove('show');
        document.body.style.overflow = 'auto';
    }
    
    // 点击关闭按钮
    closeArticleDetailBtn.addEventListener('click', function() {
        closeArticleDetailModal();
    });
    
    // 点击遮罩层关闭模态框
    articleDetailModal.addEventListener('click', function(e) {
        if (e.target === articleDetailModal) {
            closeArticleDetailModal();
        }
    });
    
    // 按ESC键关闭模态框
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape' && articleDetailModal.classList.contains('show')) {
            closeArticleDetailModal();
        }
    });
});
