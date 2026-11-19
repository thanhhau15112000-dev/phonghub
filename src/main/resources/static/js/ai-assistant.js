(() => {
    const csrf = (form) => {
        const input = form.querySelector('input[name="_csrf"]');
        return input ? input.value : null;
    };

    const showResult = (target, response) => {
        target.replaceChildren();
        const answer = document.createElement('p');
        answer.className = 'ai-answer-text';
        answer.textContent = response.answer || 'Trợ lý chưa có câu trả lời.';
        target.appendChild(answer);
        if (Array.isArray(response.sources) && response.sources.length > 0) {
            const heading = document.createElement('p');
            heading.className = 'ai-sources-heading';
            heading.textContent = 'Nguồn';
            const list = document.createElement('ul');
            response.sources.forEach((source) => {
                const item = document.createElement('li');
                item.textContent = source.label;
                list.appendChild(item);
            });
            target.append(heading, list);
        }
        target.style.display = 'block';
    };

    const request = async (url, payload, form, target, button) => {
        const headers = { Accept: 'application/json' };
        const token = form ? csrf(form) : null;
        if (token) headers['X-CSRF-TOKEN'] = token;
        const options = { method: 'POST', headers };
        if (payload) {
            headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(payload);
        }
        button.disabled = true;
        target.textContent = 'Đang xử lý…';
        target.style.display = 'block';
        try {
            const response = await fetch(url, options);
            const body = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(body.detail || 'Trợ lý hiện không thể trả lời. Dữ liệu PhongHub vẫn xem được bình thường.');
            showResult(target, body);
        } catch (error) {
            target.textContent = error.message || 'Trợ lý hiện không thể trả lời. Dữ liệu PhongHub vẫn xem được bình thường.';
        } finally {
            button.disabled = false;
        }
    };

    document.addEventListener('submit', (event) => {
        const form = event.target.closest('form[data-ai-form]');
        if (!form) return;
        event.preventDefault();
        const result = form.parentElement.querySelector('.ai-answer');
        const button = form.querySelector('button[type="submit"]');
        if (!result || !button) return;
        const payload = { question: form.elements.question ? form.elements.question.value : '' };
        if (form.elements.propertyId) payload.propertyId = form.elements.propertyId.value;
        request(form.dataset.aiEndpoint, payload, form, result, button);
    });

    document.addEventListener('click', (event) => {
        const button = event.target.closest('[data-ai-analysis]');
        if (!button) return;
        const target = document.getElementById(button.dataset.aiTarget);
        if (target) request(button.dataset.aiEndpoint, null, button.closest('section'), target, button);
    });
})();
