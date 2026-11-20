const setup = document.querySelector('#setup');
const setupForm = document.querySelector('#setup-form');
const loginForm = document.querySelector('#login-form');
const loginUsername = document.querySelector('#login-username');
const loginPassword = document.querySelector('#login-password');
const loginButton = document.querySelector('#login-button');
const loginError = document.querySelector('#login-error');
const room = document.querySelector('#room');
const position = document.querySelector('#target-position');
const resume = document.querySelector('#resume-text');
const setupError = document.querySelector('#setup-error');
const startButton = document.querySelector('#start-button');
const roomPosition = document.querySelector('#room-position');
const progress = document.querySelector('#room-progress');
const transcript = document.querySelector('#transcript');
const messages = document.querySelector('#messages');
const composer = document.querySelector('#composer');
const answer = document.querySelector('#answer-text');
const answerError = document.querySelector('#answer-error');
const sendButton = document.querySelector('#send-button');
const endButton = document.querySelector('#end-button');
const restartButton = document.querySelector('#restart-button');
const reauthButton = document.querySelector('#reauth-button');
const roomError = document.querySelector('#room-error');
const historyOpen = document.querySelector('#history-open');
const historyDialog = document.querySelector('#history-dialog');
const historyStatus = document.querySelector('#history-status');
const historyError = document.querySelector('#history-error');
const historyList = document.querySelector('#history-list');
const historyMore = document.querySelector('#history-more');
const reviewTemplate = document.querySelector('#round-review-template');
const practiceDialog = document.querySelector('#practice-dialog');
const practiceQuestion = document.querySelector('#practice-question');
const practiceForm = document.querySelector('#practice-form');
const practiceAnswer = document.querySelector('#practice-answer');
const practiceSubmit = document.querySelector('#practice-submit');
const practiceStatus = document.querySelector('#practice-status');
const practiceError = document.querySelector('#practice-error');
const practiceResult = document.querySelector('#practice-result');
const practiceResultTitle = document.querySelector('#practice-result-title');
const practiceFeedback = document.querySelector('#practice-feedback');
const practiceScoringPoints = document.querySelector('#practice-scoring-points');
const practiceSource = document.querySelector('#practice-source');
const practiceExplain = document.querySelector('#practice-explain');
const practiceExplanation = document.querySelector('#practice-explanation');

let session = null;
let pending = false;
let token = '';
let userId = null;
let historyPage = 1;
let historyBusy = false;
let practice = null;

const interruptedMessage = '连接中断，没收到保存确认。你的输入还在。';

function showLogin(message = '') {
    room.hidden = true;
    setup.hidden = false;
    setupForm.hidden = true;
    loginForm.hidden = false;
    loginUsername.readOnly = Boolean(session);
    setError(loginError, message);
    (session ? loginPassword : loginUsername).focus();
}

async function login(event) {
    event.preventDefault();
    if (loginButton.disabled) {
        return;
    }
    loginButton.disabled = true;
    setError(loginError);
    try {
        const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: loginUsername.value, password: loginPassword.value })
        });
        const result = await response.json().catch(() => null);
        if (!response.ok || !result?.data?.token) {
            throw new Error(result?.message || '登录失败，请稍后再试。');
        }
        if (session && userId !== result.data.userId) {
            throw new Error('请用创建这场面试的账号登录。');
        }

        token = result.data.token;
        userId = result.data.userId;
        loginPassword.value = '';
        loginForm.hidden = true;
        setupForm.hidden = false;
        reauthButton.hidden = true;
        setError(setupError);
        setError(roomError);
        if (session) {
            setup.hidden = true;
            room.hidden = false;
            setError(answerError);
            updateRoomActions();
            answer.focus({ preventScroll: true });
        } else {
            startButton.disabled = true;
            historyOpen.disabled = true;
            try {
                const sessionId = new URLSearchParams(location.search).get('sessionId');
                if (!await restoreInterview(sessionId)) {
                    position.focus();
                }
            } catch (error) {
                if (token) {
                    setError(setupError, error.message);
                    position.focus();
                } else {
                    showLogin(error.message);
                }
            } finally {
                startButton.disabled = false;
                historyOpen.disabled = false;
            }
        }
    } catch (error) {
        setError(loginError, error.message);
    } finally {
        loginButton.disabled = false;
    }
}

async function requestInterview(path, method = 'GET', body) {
    const headers = { Authorization: `Bearer ${token}` };
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }
    let response;
    try {
        response = await fetch(path, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
    } catch {
        throw new Error('连接中断，请稍后重试。');
    }
    if (response.status === 401) {
        token = '';
        throw new Error('登录已失效，请重新登录。');
    }
    const result = await response.json().catch(() => null);
    if (!response.ok || result?.code !== 0) {
        throw new Error(result?.message || `请求失败（${response.status}）`);
    }
    return result.data;
}

async function restoreInterview(sessionId) {
    const path = sessionId ? `/api/interview/${encodeURIComponent(sessionId)}` : '/api/interview/ongoing';
    const detail = await requestInterview(path);
    if (!detail) {
        return false;
    }
    const { id, targetPosition, currentRound, status } = detail.session;
    session = { sessionId: id, currentRound, status };
    messages.replaceChildren();
    setup.hidden = true;
    room.hidden = false;
    roomPosition.textContent = targetPosition;
    progress.textContent = `第 ${currentRound} 轮`;
    for (const { role, content, roundNo } of detail.messages) {
        const { item } = appendMessage(role === 'INTERVIEWER' ? 'interviewer' : 'candidate', content);
        if (role === 'CANDIDATE' && content.trim()) {
            item.dataset.roundNo = roundNo;
        }
    }
    if (status === 'ENDED') {
        showEnded();
    } else {
        composer.hidden = false;
        answer.focus({ preventScroll: true });
    }
    updateRoomActions();
    return true;
}

function setHistoryBusy(busy) {
    historyBusy = busy;
    historyOpen.disabled = busy;
    historyMore.disabled = busy;
    for (const button of historyList.querySelectorAll('button')) {
        button.disabled = busy;
    }
}

function openHistory() {
    historyPage = 1;
    historyList.replaceChildren();
    historyMore.hidden = true;
    historyDialog.showModal();
    return loadEndedInterviews();
}

async function loadEndedInterviews() {
    if (historyBusy) {
        return;
    }
    const size = 10;
    setHistoryBusy(true);
    setError(historyError);
    setError(historyStatus, '正在读取你的面试记录…');
    try {
        const page = await requestInterview(`/api/interview/history?page=${historyPage}&size=${size}&status=ENDED`);
        if (!historyDialog.open) {
            return;
        }
        for (const { id, targetPosition, createTime } of page.records) {
            const item = document.createElement('li');
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'button button-secondary';
            const positionName = document.createElement('span');
            positionName.textContent = targetPosition;
            const date = document.createElement('small');
            date.textContent = createTime?.replace('T', ' ').slice(0, 16) || `会话 ${id}`;
            button.append(positionName, date);
            button.addEventListener('click', () => openEndedInterview(id));
            item.append(button);
            historyList.append(item);
        }
        historyMore.hidden = historyPage * size >= page.total;
        historyMore.textContent = '加载更多';
        historyPage += 1;
        setError(historyStatus, !historyList.childElementCount && historyMore.hidden ? '暂无已结束的面试。' : '');
    } catch (error) {
        if (historyDialog.open) {
            if (!token) {
                historyDialog.close();
                showLogin(error.message);
            } else {
                setError(historyError, error.message);
                historyMore.hidden = false;
                historyMore.textContent = '重试';
            }
            setError(historyStatus);
        }
    } finally {
        setHistoryBusy(false);
    }
}

async function openEndedInterview(sessionId) {
    if (historyBusy || pending) {
        return;
    }
    pending = true;
    startButton.disabled = true;
    setHistoryBusy(true);
    setError(historyError);
    setError(historyStatus, '正在打开这场面试…');
    try {
        if (!await restoreInterview(sessionId)) {
            throw new Error('没有找到这场面试。');
        }
        const url = new URL(location.href);
        url.searchParams.set('sessionId', sessionId);
        history.replaceState(null, '', url);
        historyDialog.close();
        (messages.querySelector('[data-review]') ?? restartButton).focus({ preventScroll: true });
    } catch (error) {
        if (!token) {
            historyDialog.close();
            showLogin(error.message);
        } else {
            setError(historyError, error.message);
        }
    } finally {
        pending = false;
        startButton.disabled = false;
        setError(historyStatus);
        setHistoryBusy(false);
        updateRoomActions();
    }
}

async function streamInterview(path, body, onDelta) {
    let response;
    try {
        response = await fetch(`${path}?stream=true`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Accept: 'text/event-stream, application/json;q=0.9',
                Authorization: `Bearer ${token}`
            },
            body: JSON.stringify(body)
        });
    } catch {
        throw new Error(interruptedMessage);
    }

    if (!response.ok) {
        if (response.status === 401) {
            token = '';
            throw new Error('登录已失效，请重新登录。');
        }
        const error = await response.json().catch(() => null);
        const message = response.status === 429 ? '请求太频繁，请稍等一会儿再试。' : `请求失败（${response.status}）`;
        throw Object.assign(new Error(error?.message || message), { code: error?.code });
    }
    if (!response.body || !response.headers.get('content-type')?.includes('text/event-stream')) {
        throw new Error('没有收到面试对话，请确认后端已更新并重启。');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    try {
        while (true) {
            const { value, done } = await reader.read().catch(() => {
                throw new Error(interruptedMessage);
            });
            if (done) {
                throw new Error(interruptedMessage);
            }
            buffer += decoder.decode(value, { stream: true });

            // HTTP 分块可能拆开 UTF-8 字符、SSE 分隔符，也可能一次包含多个事件。
            let boundary;
            while ((boundary = /\r?\n\r?\n/.exec(buffer)) !== null) {
                const frame = buffer.slice(0, boundary.index);
                buffer = buffer.slice(boundary.index + boundary[0].length);
                const { type, data } = parseEvent(frame);
                if (type === 'delta') {
                    onDelta(data.text);
                } else if (type === 'done') {
                    return data;
                } else if (type === 'problem') {
                    throw Object.assign(new Error(data.message), { code: data.code });
                }
            }
        }
    } finally {
        await reader.cancel().catch(() => {});
        reader.releaseLock();
    }
}

function parseEvent(frame) {
    let type = '';
    const payload = [];
    for (const line of frame.split(/\r?\n/)) {
        if (line.startsWith('event:')) {
            type = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
            payload.push(line.slice(5).replace(/^ /, ''));
        }
    }
    if (!payload.length) {
        return {};
    }
    try {
        return { type, data: JSON.parse(payload.join('\n')) };
    } catch {
        throw new Error(interruptedMessage);
    }
}

function setError(element, message = '') {
    element.textContent = message;
    element.hidden = !message;
}

function addRoundReview(item) {
    const panel = reviewTemplate.content.firstElementChild.cloneNode(true);
    const button = panel.querySelector('[data-review]');
    const status = panel.querySelector('[data-status]');
    const feedback = panel.querySelector('[data-feedback]');
    const missing = panel.querySelector('[data-missing]');
    const practiceButton = panel.querySelector('[data-practice]');
    const errorText = panel.querySelector('[data-error]');
    item.append(panel);

    button.addEventListener('click', async () => {
        if (pending || !token) {
            return;
        }
        pending = true;
        updateRoomActions();
        setError(errorText);
        setError(status, '正在复盘这一轮…');
        try {
            const result = await requestInterview(
                `/api/interview/${session.sessionId}/review?roundNo=${item.dataset.roundNo}`, 'POST');
            button.hidden = true;
            setError(feedback, result.feedback);
            setError(missing, result.missingPoint);
            if (result.practiceQuestionId != null) {
                practiceButton.hidden = false;
                practiceButton.addEventListener('click', () => openPractice(result.practiceQuestionId, result.missingPoint));
                practiceButton.focus({ preventScroll: true });
            } else {
                feedback.focus({ preventScroll: true });
            }
        } catch (error) {
            setError(errorText, error.message);
            reauthButton.hidden = Boolean(token);
        } finally {
            setError(status);
            pending = false;
            updateRoomActions();
        }
    });
}

function updatePracticeActions() {
    const waiting = Boolean(practice?.pending);
    practiceSubmit.disabled = waiting || !practiceAnswer.value.trim();
    practiceAnswer.readOnly = waiting;
    practiceExplain.disabled = waiting;
}

async function runPracticeRequest(message, request, render) {
    if (!practice || practice.pending) {
        return;
    }
    const current = practice;
    current.pending = true;
    updatePracticeActions();
    setError(practiceError);
    setError(practiceStatus, message);
    try {
        const result = await request();
        if (practiceDialog.open && practice === current) {
            render(result);
        }
    } catch (error) {
        if (practiceDialog.open && practice === current) {
            setError(practiceError, error.message);
        }
        reauthButton.hidden = Boolean(token);
    } finally {
        if (practiceDialog.open && practice === current) {
            current.pending = false;
            setError(practiceStatus);
            updatePracticeActions();
        }
    }
}

function openPractice(questionId, missingPoint) {
    if (!token) {
        showLogin('登录已失效，请重新登录。');
        return;
    }
    practice = { questionId, missingPoint, pending: false };
    practiceQuestion.textContent = '';
    practiceForm.reset();
    practiceForm.hidden = true;
    practiceResult.hidden = true;
    setError(practiceExplanation);
    practiceDialog.showModal();
    return runPracticeRequest('正在加载题目…',
        () => requestInterview(`/api/interview/practice/${questionId}`),
        result => {
            practiceQuestion.textContent = result.questionText;
            practiceForm.hidden = false;
            practiceAnswer.focus({ preventScroll: true });
        });
}

function submitPractice(event) {
    event.preventDefault();
    const answerText = practiceAnswer.value;
    if (!practice || !answerText.trim()) {
        return;
    }
    return runPracticeRequest('正在核对评分点…',
        () => requestInterview(`/api/interview/practice/${practice.questionId}/answer`, 'POST', { answerText }),
        result => {
            practiceFeedback.textContent = result.feedback;
            practiceScoringPoints.textContent = result.scoringPoints;
            practiceSource.hidden = !/^https?:\/\//i.test(result.sourceUrl);
            if (!practiceSource.hidden) {
                practiceSource.href = result.sourceUrl;
            }
            practiceExplain.hidden = !practice.missingPoint;
            setError(practiceExplanation);
            practiceResult.hidden = false;
            practiceResultTitle.focus();
        });
}

function explainPractice() {
    if (!practice?.missingPoint) {
        return;
    }
    const question = `请解释这道专项题的这个知识点。题目：${practiceQuestion.textContent}\n要解释的缺漏：${practice.missingPoint}`;
    return runPracticeRequest('正在从题库资料查找解释…',
        () => requestInterview('/ask/question', 'POST', { question }),
        result => {
            if (!result || !window.marked?.parse || !window.DOMPurify?.isSupported) {
                setError(practiceExplanation, result);
                return;
            }
            practiceExplanation.innerHTML = window.DOMPurify.sanitize(window.marked.parse(result), {
                USE_PROFILES: { html: true }
            });
            practiceExplanation.hidden = !practiceExplanation.hasChildNodes();
        });
}

function appendMessage(role, text) {
    const item = document.createElement('li');
    item.className = `message message-${role}`;
    const speaker = document.createElement('span');
    speaker.className = 'message-speaker';
    speaker.textContent = role === 'interviewer' ? '面试官' : '你';
    const content = document.createElement('p');
    content.className = 'message-text';
    content.textContent = text;
    item.append(speaker, content);
    messages.append(item);
    transcript.scrollTop = transcript.scrollHeight;
    return { item, content };
}

function pendingQuestion() {
    const { item, content } = appendMessage('interviewer', '正在想下一句…');
    item.setAttribute('aria-busy', 'true');
    item.classList.add('is-waiting');

    function update(text, append) {
        const nearBottom = transcript.scrollHeight - transcript.scrollTop - transcript.clientHeight < 48;
        const waiting = item.classList.contains('is-waiting');
        content.textContent = append && !waiting ? content.textContent + text : text;
        item.classList.remove('is-waiting');
        if (nearBottom) {
            transcript.scrollTop = transcript.scrollHeight;
        }
    }

    return {
        add: text => update(text, true),
        finish(text) {
            update(text, false);
            item.removeAttribute('aria-busy');
        },
        remove: () => item.remove()
    };
}

function updateRoomActions() {
    const ongoing = session && session.status !== 'ENDED';
    sendButton.disabled = !ongoing || pending || !token || !answer.value.trim();
    endButton.hidden = !ongoing || !token;
    endButton.disabled = pending;
    restartButton.disabled = pending;
    for (const button of messages.querySelectorAll('[data-review]')) {
        button.disabled = pending || !token;
    }
}

function showEnded() {
    session.status = 'ENDED';
    composer.hidden = true;
    progress.textContent = '面试已结束';
    restartButton.hidden = false;
    for (const item of messages.querySelectorAll('[data-round-no]')) {
        addRoundReview(item);
    }
    restartButton.focus({ preventScroll: true });
}

async function endInterview() {
    if (pending || !token || !session || session.status === 'ENDED') {
        return;
    }
    pending = true;
    updateRoomActions();
    setError(roomError);
    try {
        await requestInterview(`/api/interview/${session.sessionId}/end`, 'POST');
        answer.value = '';
        showEnded();
    } catch (error) {
        setError(roomError, error.message);
        reauthButton.hidden = Boolean(token);
        if (!token) {
            reauthButton.focus({ preventScroll: true });
        }
    } finally {
        pending = false;
        updateRoomActions();
    }
}

async function start() {
    if (pending) {
        return;
    }
    if (!token) {
        showLogin();
        return;
    }
    const targetPosition = position.value.trim();
    const resumeText = resume.value.trim();
    if (!targetPosition || !resumeText) {
        setError(setupError, '请填上目标岗位和简历。');
        (targetPosition ? resume : position).focus();
        return;
    }

    pending = true;
    startButton.disabled = true;
    setError(setupError);
    setError(roomError);
    roomPosition.textContent = targetPosition;
    progress.textContent = '首题准备中';
    setup.hidden = true;
    room.hidden = false;
    const question = pendingQuestion();
    try {
        session = await streamInterview('/api/interview/start', { resumeText, targetPosition }, question.add);
        composer.hidden = false;
        question.finish(session.question);
        progress.textContent = `第 ${session.currentRound} 轮`;
        answer.focus({ preventScroll: true });
    } catch (error) {
        question.remove();
        let message = error.message;
        if (error.code === 1003) {
            try {
                if (await restoreInterview()) {
                    return;
                }
            } catch (restoreError) {
                message = restoreError.message;
            }
        }
        room.hidden = true;
        setup.hidden = false;
        setError(setupError, message);
        if (token) {
            resume.focus({ preventScroll: true });
        } else {
            showLogin(message);
        }
    } finally {
        pending = false;
        startButton.disabled = false;
        updateRoomActions();
    }
}

async function submitAnswer() {
    const answerText = answer.value;
    if (pending || !token || !session || session.status === 'ENDED' || !answerText.trim()) {
        return;
    }

    pending = true;
    updateRoomActions();
    answer.readOnly = true;
    setError(answerError);
    progress.textContent = '等待面试官回复';
    const candidate = appendMessage('candidate', answerText);
    candidate.item.dataset.roundNo = session.currentRound;
    const question = pendingQuestion();
    try {
        session = await streamInterview(`/api/interview/${session.sessionId}/answer`, {
            roundNo: session.currentRound,
            answerText
        }, question.add);
        answer.value = '';
        if (session.status === 'ENDED') {
            question.finish('这场面试就到这里。你可以往上回看刚才的问答。');
            showEnded();
        } else {
            question.finish(session.question);
            progress.textContent = `第 ${session.currentRound} 轮`;
        }
    } catch (error) {
        question.remove();
        candidate.item.remove();
        progress.textContent = `第 ${session.currentRound} 轮`;
        setError(answerError, error.message);
        reauthButton.hidden = Boolean(token);
    } finally {
        pending = false;
        answer.readOnly = false;
        updateRoomActions();
        if (!composer.hidden) {
            answer.focus({ preventScroll: true });
        }
    }
}

loginForm.addEventListener('submit', login);
setupForm.addEventListener('submit', event => event.preventDefault());
composer.addEventListener('submit', event => event.preventDefault());
answer.addEventListener('input', updateRoomActions);
answer.addEventListener('keydown', event => {
    // 一些浏览器在输入法确认候选词时只提供 keyCode 229。
    if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) {
        return;
    }
    event.preventDefault();
    if (!event.repeat) {
        submitAnswer();
    }
});
startButton.addEventListener('click', start);
sendButton.addEventListener('click', submitAnswer);
endButton.addEventListener('click', endInterview);
historyOpen.addEventListener('click', openHistory);
historyMore.addEventListener('click', loadEndedInterviews);
practiceDialog.addEventListener('close', () => { practice = null; });
practiceForm.addEventListener('submit', submitPractice);
practiceAnswer.addEventListener('input', () => {
    practiceResult.hidden = true;
    updatePracticeActions();
});
practiceExplain.addEventListener('click', explainPractice);
reauthButton.addEventListener('click', () => showLogin());
restartButton.addEventListener('click', () => {
    session = null;
    messages.replaceChildren();
    const url = new URL(location.href);
    url.searchParams.delete('sessionId');
    history.replaceState(null, '', url);
    setError(answerError);
    setError(roomError);
    restartButton.hidden = true;
    room.hidden = true;
    setup.hidden = false;
    updateRoomActions();
    position.focus();
});
