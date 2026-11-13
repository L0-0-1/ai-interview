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
const restartButton = document.querySelector('#restart-button');
const reauthButton = document.querySelector('#reauth-button');

let session = null;
let pending = false;
let token = '';
let userId = null;

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
        if (session) {
            setup.hidden = true;
            room.hidden = false;
            setError(answerError);
            updateSendButton();
            answer.focus({ preventScroll: true });
        } else {
            position.focus();
        }
    } catch (error) {
        setError(loginError, error.message);
    } finally {
        loginButton.disabled = false;
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
        throw new Error(error?.message || message);
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
                    throw new Error(data.message);
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

function updateSendButton() {
    sendButton.disabled = pending || !token || !answer.value.trim();
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
        room.hidden = true;
        setup.hidden = false;
        setError(setupError, error.message);
        if (token) {
            resume.focus({ preventScroll: true });
        } else {
            showLogin(error.message);
        }
    } finally {
        pending = false;
        startButton.disabled = false;
        updateSendButton();
    }
}

async function submitAnswer() {
    const answerText = answer.value;
    if (pending || !token || !session || session.status === 'ENDED' || !answerText.trim()) {
        return;
    }

    pending = true;
    updateSendButton();
    answer.readOnly = true;
    setError(answerError);
    progress.textContent = '等待面试官回复';
    const candidate = appendMessage('candidate', answerText);
    const question = pendingQuestion();
    try {
        session = await streamInterview(`/api/interview/${session.sessionId}/answer`, {
            roundNo: session.currentRound,
            answerText
        }, question.add);
        answer.value = '';
        if (session.status === 'ENDED') {
            composer.hidden = true;
            question.finish('这场面试就到这里。你可以往上回看刚才的问答。');
            progress.textContent = `已完成 ${session.currentRound} 轮`;
            restartButton.hidden = false;
            restartButton.focus({ preventScroll: true });
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
        updateSendButton();
        if (!composer.hidden) {
            answer.focus({ preventScroll: true });
        }
    }
}

loginForm.addEventListener('submit', login);
setupForm.addEventListener('submit', event => event.preventDefault());
composer.addEventListener('submit', event => event.preventDefault());
answer.addEventListener('input', updateSendButton);
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
reauthButton.addEventListener('click', () => showLogin());
restartButton.addEventListener('click', () => {
    session = null;
    messages.replaceChildren();
    setError(answerError);
    restartButton.hidden = true;
    room.hidden = true;
    setup.hidden = false;
    position.focus();
});
