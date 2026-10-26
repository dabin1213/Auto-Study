// 퀴즈 결과 표시 + 다 풀면 채점·풀이 기록 저장 (채점은 서버가 함)
// 사용 페이지: quiz.html, history.html, team-space.html

// 단답형 답 비교용 정규화 (서버 HistoryController.normalize와 같은 규칙)
function normalizeAnswer(s) {
    return String(s == null ? '' : s).toLowerCase().replace(/[\s!-\/:-@\[-`{-~]/g, '');
}

function makeTypeTag(type) {
    const t = document.createElement('span');
    t.textContent = type === 'OX' ? 'O/X' : type;
    t.style.cssText = 'display:inline-block;margin-right:8px;padding:1px 10px;border-radius:999px;' +
        'background:#e3f2fd;color:#1565c0;border:1px solid #90caf9;font-size:0.72rem;font-weight:700;vertical-align:middle;';
    return t;
}

// 난이도 · 문제 유형 · 요약 길이 한 줄 표시
function metaText(data) {
    const parts = [];
    if (data.difficulty) parts.push('난이도 ' + data.difficulty);
    if (data.question_types) {
        parts.push('유형 ' + data.question_types.split(',').map(t => t === 'OX' ? 'O/X' : t).join(', '));
    }
    if (data.summary_length) parts.push('요약 ' + data.summary_length);
    return parts.join(' · ');
}

function renderResult(data) {
    const box = document.getElementById('resultBox');
    box.innerHTML = '';
    const questions = JSON.parse(data.quiz_data);
    const total = questions.length;
    const answers = new Array(total).fill('');   // 문제별로 낸 답 (보기 번호 또는 입력한 글자)
    let answered = 0;

    if (data.file_name) {
        const head = document.createElement('h3');
        head.textContent = data.file_name;
        box.appendChild(head);
    }

    const meta = metaText(data);
    if (meta) {
        const m = document.createElement('div');
        m.textContent = meta;
        m.style.cssText = 'font-size:0.82rem;color:var(--text-muted);margin-bottom:16px;';
        box.appendChild(m);
    }

    const sTitle = document.createElement('h3');
    sTitle.textContent = '요약';
    const sBody = document.createElement('p');
    sBody.className = 'result-summary';
    sBody.textContent = data.summary;
    box.append(sTitle, sBody);

    const qTitle = document.createElement('h3');
    qTitle.textContent = `퀴즈 (${total}문항)`;
    box.appendChild(qTitle);

    const scoreBox = document.createElement('div');
    scoreBox.className = 'q-exp';
    scoreBox.style.display = 'none';
    scoreBox.style.fontWeight = '700';

    async function finish() {
        scoreBox.style.display = 'block';
        scoreBox.textContent = '채점 및 기록 저장 중...';
        scoreBox.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        try {
            const res = await fetch('/history', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    contentId: data.content_id,
                    answers: answers,
                    // 팀 공유 공간에서 푼 경우에만 팀 ID를 함께 보냄 (그 외에는 null)
                    teamId: data.solved_team_id ? Number(data.solved_team_id) : null
                })
            });
            const r = await res.json();
            if (res.ok) {
                scoreBox.textContent = `결과: ${r.total_count}문제 중 ${r.correct_count}문제 정답 (${r.score}점) · 기록 저장 완료 (연속 학습 ${r.streak_days}일)`;
                if (typeof onHistorySaved === 'function') onHistorySaved();
            } else {
                scoreBox.textContent = '기록 저장 실패: ' + (r.error || '알 수 없는 오류');
            }
        } catch (err) {
            scoreBox.textContent = '서버 연결 실패로 결과를 저장하지 못했어요';
        }
    }

    // 한 문제를 풀었을 때 호출: 답을 기록하고, 다 풀었으면 채점
    function done(i, given) {
        answers[i] = given;
        answered++;
        if (answered === total) finish();
    }

    questions.forEach((q, i) => {
        const type = q.type || '객관식';   // type이 없는 예전 퀴즈는 객관식

        const card = document.createElement('div');
        card.className = 'q-card';

        const qText = document.createElement('div');
        qText.className = 'q-text';
        qText.appendChild(makeTypeTag(type));
        qText.appendChild(document.createTextNode(`Q${i + 1}. ${q.question}`));
        card.appendChild(qText);

        const exp = document.createElement('div');
        exp.className = 'q-exp';
        exp.style.display = 'none';
        exp.textContent = '해설: ' + q.explanation;

        if (type === '단답형') {
            const row = document.createElement('div');
            row.style.cssText = 'display:flex;gap:8px;margin-bottom:6px;';

            const input = document.createElement('input');
            input.type = 'text';
            input.maxLength = 200;
            input.placeholder = '정답을 입력하세요';
            input.style.cssText = 'flex:1;padding:8px 12px;border:1.5px solid var(--border);border-radius:8px;font-size:0.86rem;outline:none;';

            const btn = document.createElement('button');
            btn.textContent = '정답 확인';
            btn.style.cssText = 'padding:8px 16px;border:none;border-radius:8px;background:var(--teal);color:#fff;font-size:0.82rem;font-weight:700;cursor:pointer;';

            const result = document.createElement('div');
            result.className = 'q-opt';
            result.style.display = 'none';
            result.style.cursor = 'default';

            const submit = () => {
                if (card.dataset.done) return;
                const given = input.value.trim();
                if (!given) {
                    alert('답을 입력해주세요.');
                    return;
                }
                card.dataset.done = '1';
                input.disabled = true;
                btn.disabled = true;

                const g = normalizeAnswer(given);
                const ok = g !== '' && [q.answer].concat(q.acceptable || [])
                    .some(a => normalizeAnswer(a) === g);
                result.style.display = 'block';
                result.classList.add(ok ? 'correct' : 'wrong');
                result.textContent = ok ? `정답입니다: ${q.answer}` : `오답이에요. 정답: ${q.answer}`;
                exp.style.display = 'block';
                done(i, given);
            };
            btn.onclick = submit;
            input.onkeydown = e => { if (e.key === 'Enter') submit(); };

            row.append(input, btn);
            card.append(row, result);
        } else {
            (q.options || []).forEach((opt, j) => {
                const o = document.createElement('div');
                o.className = 'q-opt';
                o.textContent = type === 'OX' ? opt : `${j + 1}. ${opt}`;
                o.onclick = () => {
                    if (card.dataset.done) return;
                    card.dataset.done = '1';
                    o.classList.add(j === q.answer ? 'correct' : 'wrong');
                    card.querySelectorAll('.q-opt')[q.answer].classList.add('correct');
                    exp.style.display = 'block';
                    done(i, String(j));
                };
                card.appendChild(o);
            });
        }

        card.appendChild(exp);
        box.appendChild(card);
    });

    box.appendChild(scoreBox);
    box.style.display = 'block';
    box.scrollIntoView({ behavior: 'smooth' });
}