# React search page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Одна страница RepairRadar с выбором работы, периода и всеми найденными адресами.

**Architecture:** React + Vite в frontend; относительные API URL и прокси к Spring Boot. Компонент WorkTypeSelect загружает справочник при открытии. api.js проверяет ответы и преобразует даты YYYY-MM в MM.yyyy. App управляет формой и результатами.

**Tech Stack:** Последний стабильный React из npm latest, Vite, Playwright, CSS.

**Spec:** Запрос пользователя в текущей беседе; контракт ProgramSearchController.java.

## Global Constraints

- Одна страница, русский интерфейс, все адреса без пагинации.
- GET /api/programs/work-types только при открытии списка.
- GET /api/programs/search с workTypeName, startDate, endDate.
- Существующий backend не меняется.

## Review Focus

- Пустые справочники и результаты: понятные состояния.
- Ошибки сети и БД: русский текст и возможность повторить запрос.
- Даты обязательны, начало не позже конца; формат API MM.yyyy.
- Клавиатура и мобильный экран: доступный список, видимый фокус, отсутствие переполнения.
- Повторный поиск и изменение формы: не показывать старые адреса как новые результаты.

### Task 1: Интерфейс и подключение

**Files:** frontend/package.json, index.html, vite.config.js, src/main.jsx, src/App.jsx, src/WorkTypeSelect.jsx, src/api.js, src/styles.css; .gitignore; README.md.

**Interfaces:** loadWorkTypes(): Promise<string[]>; searchAddresses({workTypeName,startDate,endDate}): Promise<string[]>.

- [x] Установить react@latest и react-dom@latest с точными версиями и lockfile, vite и @playwright/test.
- [x] Реализовать API: `new URLSearchParams({workTypeName, startDate: toApiMonth(startDate), endDate: toApiMonth(endDate)})`; проверять Array.isArray и тип каждого элемента.
- [x] Реализовать список, месяцы, кнопку поиска и состояния результата; обеспечить управление стрелками, Escape, Enter.
- [x] Настроить proxy /api на BACKEND_URL или http://localhost:8080 и описать запуск npm run dev / npm run build.

### Task 2: Проверка страницы

**Files:** frontend/playwright.config.js, frontend/tests/search.spec.js.

- [x] Проверить в браузере отложенную загрузку списка, выбор работы, формат дат и отображение всех адресов через маршруты Playwright.
- [x] Проверить пустой ответ, ошибку и повторный запрос, неверный диапазон и клавиатуру.
- [x] Выполнить `npm run build` и `npm test`; проверить мобильную ширину и снимок страницы.
