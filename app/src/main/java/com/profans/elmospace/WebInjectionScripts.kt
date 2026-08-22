package com.profans.elmospace

import org.json.JSONObject

object WebInjectionScripts {
    fun imagePreviewObserver(bridgeName: String): String {
        val bridgeNameLiteral = JSONObject.quote(bridgeName)
        return """
        (function() {
            const bridge = window[$bridgeNameLiteral];
            if (!document.documentElement || !bridge) return false;
            if (window.__androidPreviewObserver) {
                window.__androidPreviewObserver.disconnect();
            }
            window.__androidPreviewState = null;
            const notify = function() {
                const modal = document.querySelector('.vel-modal');
                const visible = !!modal && getComputedStyle(modal).display !== 'none';
                if (window.__androidPreviewState !== visible) {
                    window.__androidPreviewState = visible;
                    bridge.setImagePreviewVisible(visible);
                }
            };
            window.__androidPreviewObserver = new MutationObserver(notify);
            window.__androidPreviewObserver.observe(document.documentElement, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['class', 'style']
            });
            notify();
            return true;
        })();
        """.trimIndent()
    }

    fun nativeSettingsShortcut(bridgeName: String): String {
        val bridgeNameLiteral = JSONObject.quote(bridgeName)
        return """
        (function() {
            const androidBridgeName = $bridgeNameLiteral;
            const getAndroidBridge = function() {
                return window[androidBridgeName];
            };
            const styleId = 'android-history-shortcut-style';
            if (!document.getElementById(styleId)) {
                const style = document.createElement('style');
                style.id = styleId;
                style.textContent = `
                    .android-history-shortcut {
                        position: fixed;
                        right: .62rem;
                        top: .11rem;
                        width: .27rem;
                        height: .27rem;
                        padding: 0;
                        border: 0;
                        outline: 0;
                        background: transparent;
                        color: #fff;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        z-index: 103;
                        -webkit-tap-highlight-color: transparent;
                    }
                    @media (min-width: 551px) {
                        .android-history-shortcut {
                            right: calc((100vw - 550px) / 2 + .62rem);
                        }
                    }
                    .android-history-shortcut svg {
                        width: .25rem;
                        height: .25rem;
                        display: block;
                        pointer-events: none;
                    }
                `;
                document.head.appendChild(style);
            }

            const ensureHistoryShortcut = function() {
                const settings = document.querySelector(
                    '.home_main > .head > .set, .home_main > .head_pc > .set'
                );
                const current = document.querySelector('.android-history-shortcut');
                if (!settings) {
                    if (current) current.remove();
                    return false;
                }
                if (current) return true;

                const button = document.createElement('button');
                button.type = 'button';
                button.className = 'android-history-shortcut';
                button.setAttribute('aria-label', '历史记录');
                button.setAttribute('title', '历史记录');
                button.innerHTML =
                    '<svg viewBox="0 0 24 24" aria-hidden="true">' +
                    '<path d="M3.5 12a8.5 8.5 0 1 0 2.1-5.6" fill="none" ' +
                    'stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>' +
                    '<path d="M3.5 4.8v4.3h4.3M12 7.2v5.2l3.4 2" fill="none" ' +
                    'stroke="currentColor" stroke-width="1.8" stroke-linecap="round" ' +
                    'stroke-linejoin="round"/></svg>';
                document.body.appendChild(button);
                return true;
            };

            if (!window.__androidHeaderShortcutsV2Installed) {
                document.addEventListener('click', function(event) {
                    const bridge = getAndroidBridge();
                    if (!event.target || !event.target.closest || !bridge) return;
                    const historyTarget = event.target.closest('.android-history-shortcut');
                    const settingsTarget = event.target.closest(
                        '.home_main .head .set, .home_main .head_pc .set'
                    );
                    if (!historyTarget && !settingsTarget) return;
                    event.preventDefault();
                    event.stopPropagation();
                    event.stopImmediatePropagation();
                    if (historyTarget) {
                        bridge.openBrowsingHistory();
                    } else {
                        bridge.openNativeSettings();
                    }
                }, true);

                const observer = new MutationObserver(function() {
                    ensureHistoryShortcut();
                });
                const observerTarget = document.body || document.documentElement;
                if (observerTarget) {
                    observer.observe(observerTarget, { childList: true, subtree: true });
                    window.__androidHeaderShortcutsV2Observer = observer;
                }
                window.__androidHeaderShortcutsV2Installed = true;
            }

            ensureHistoryShortcut();
            return true;
        })();
        """.trimIndent()
    }

    fun hideWebNavCss(): String =
        """
        (function() {
            const styleId = 'android-hide-web-nav-style';
            let style = document.getElementById(styleId);
            if (!style) {
                style = document.createElement('style');
                style.id = styleId;
                document.head.appendChild(style);
            }
            // 仅隐藏 home 根组件的五项底栏，不能影响消息页内部的 .content > .nav。
            style.textContent = '.home_main > .nav { display: none !important; }';
            return true;
        })();
        """.trimIndent()

    fun removeHideWebNavCss(): String =
        "(function(){var s=document.getElementById('android-hide-web-nav-style');if(s){s.remove();}})();"

    fun feedImagePreloader(preloadEnabled: Boolean, preloadScreens: Int): String =
        """
        (function() {
            window.__androidFeedPreloadEnabled = $preloadEnabled;
            window.__androidFeedPreloadScreens = $preloadScreens;
            if (window.__androidFeedPreloaderInstalled) {
                window.__androidPreloadNextScreen && window.__androidPreloadNextScreen();
                return true;
            }

            const loaded = new Set();
            const activeLoads = [];
            let timer = 0;

            const preload = function() {
                if (!window.__androidFeedPreloadEnabled) return;
                if (location.pathname !== '/m' && location.pathname !== '/m/') return;
                const height = window.innerHeight || document.documentElement.clientHeight;
                if (!height) return;
                const screens = Math.max(1, Math.min(10, window.__androidFeedPreloadScreens || 1));

                Array.from(document.images).forEach(function(img) {
                    const rect = img.getBoundingClientRect();
                    if (rect.bottom < height || rect.top > height * (screens + 1)) return;

                    const source = [
                        img.currentSrc,
                        img.src,
                        img.getAttribute('data-src'),
                        img.getAttribute('data-original')
                    ].map(function(value) {
                        if (!value) return '';
                        try { return new URL(value, location.href).href; } catch (_) { return ''; }
                    }).find(function(value) {
                        return value.startsWith('http://') || value.startsWith('https://');
                    });
                    if (!source || loaded.has(source)) return;

                    loaded.add(source);
                    const request = new Image();
                    const srcset = img.getAttribute('srcset') || img.getAttribute('data-srcset');
                    if (srcset) request.srcset = srcset;
                    if (img.sizes) request.sizes = img.sizes;
                    request.onload = request.onerror = function() {
                        const index = activeLoads.indexOf(request);
                        if (index >= 0) activeLoads.splice(index, 1);
                    };
                    activeLoads.push(request);
                    request.src = source;
                });
            };

            const schedule = function() {
                clearTimeout(timer);
                timer = setTimeout(preload, 250);
            };

            window.__androidPreloadNextScreen = preload;
            window.__androidFeedPreloaderInstalled = true;
            document.addEventListener('scroll', schedule, true);
            window.addEventListener('resize', schedule);
            schedule();
            return true;
        })();
        """.trimIndent()

    fun homeSliderPaginationFix(): String =
        """
        (function() {
            if (location.pathname !== '/m' && location.pathname !== '/m/') return false;

            const install = function(container) {
                if (!container || container.__androidSliderPaginationFixed) return !!container;
                const pagination = container.querySelector('.swiper-pagination');
                if (!pagination) return false;

                const slides = function() {
                    return Array.from(container.querySelectorAll('.swiper-wrapper > .swiper-slide'))
                        .filter(function(slide) { return !slide.classList.contains('swiper-slide-duplicate'); });
                };
                const bullets = function() {
                    return Array.from(pagination.querySelectorAll('.swiper-pagination-bullet'));
                };
                const activeIndex = function(items) {
                    const active = container.querySelector('.swiper-slide-active');
                    if (active) {
                        const index = items.indexOf(active);
                        if (index >= 0) return index;
                    }
                    const swiper = container.swiper || container.__swiper__ || window.swiper;
                    if (swiper && typeof swiper.activeIndex === 'number') {
                        return Math.max(0, Math.min(items.length - 1, swiper.activeIndex));
                    }
                    return Math.max(0, bullets().findIndex(function(bullet) {
                        return bullet.classList.contains('swiper-pagination-bullet-active');
                    }));
                };
                const sync = function() {
                    const items = slides();
                    const dots = bullets();
                    if (!items.length || !dots.length) return;
                    const index = activeIndex(items);
                    dots.forEach(function(dot, dotIndex) {
                        dot.classList.toggle('swiper-pagination-bullet-active', dotIndex === index);
                    });
                };
                const schedule = function(delay) {
                    window.setTimeout(sync, delay || 0);
                    window.setTimeout(sync, 80);
                    window.setTimeout(sync, 260);
                };

                ['touchend', 'mouseup', 'transitionend', 'webkitTransitionEnd'].forEach(function(name) {
                    container.addEventListener(name, function() { schedule(0); }, true);
                });
                pagination.addEventListener('click', function() { schedule(0); }, true);

                const observer = new MutationObserver(function() { schedule(0); });
                observer.observe(container, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['class', 'style']
                });

                container.__androidSliderPaginationFixed = true;
                container.__androidSliderPaginationObserver = observer;
                schedule(0);
                return true;
            };

            const tryInstall = function(attempt) {
                const container = document.querySelector('.swiper-container1');
                if (install(container)) return true;
                if (attempt < 12) window.setTimeout(function() { tryInstall(attempt + 1); }, 250);
                return false;
            };

            return tryInstall(0);
        })();
        """.trimIndent()

    fun tabletThreadSplitInterceptor(bridgeName: String): String {
        val bridgeNameLiteral = JSONObject.quote(bridgeName)
        return """
        (function() {
            if (location.pathname !== '/m' && location.pathname !== '/m/') return false;
            const bridgeName = $bridgeNameLiteral;
            const bridge = window[bridgeName];
            if (!bridge || !bridge.openTabletThreadInfo) return false;
            if (window.__androidTabletThreadSplitInstalled) return true;
            window.__androidTabletThreadSplitInstalled = true;

            const collectTopicModels = function(value, results, visited, depth) {
                if (!value || typeof value !== 'object' || depth > 5) return;
                if (visited.indexOf(value) >= 0) return;
                visited.push(value);
                if (value.topic_id) results.push(value);
                if (Array.isArray(value)) {
                    value.forEach(function(item) {
                        collectTopicModels(item, results, visited, depth + 1);
                    });
                    return;
                }
                Object.keys(value).slice(0, 80).forEach(function(key) {
                    collectTopicModels(value[key], results, visited, depth + 1);
                });
            };

            const normalizeText = function(value) {
                return String(value || '').replace(/\s+/g, ' ').trim();
            };

            const readTopicIdFromUrl = function(value) {
                try {
                    const url = new URL(String(value || ''), location.href);
                    if (url.pathname !== '/m/threadInfo') return null;
                    const id = url.searchParams.get('id');
                    return /^\d+$/.test(id || '') ? id : null;
                } catch (error) {
                    return null;
                }
            };

            const readTopicIdFromCard = function(card) {
                if (!card) return null;
                const directUrl = card.getAttribute('href') ||
                    card.getAttribute('data-url') ||
                    card.getAttribute('data-href') ||
                    '';
                const directId = readTopicIdFromUrl(directUrl);
                if (directId) return directId;
                const link = card.querySelector('a[href*="threadInfo"]');
                return link ? readTopicIdFromUrl(link.getAttribute('href')) : null;
            };

            const readCardText = function(card) {
                if (!card) return { title: '', author: '' };
                const titleNode = card.querySelector('.card_tit p, .card_m1 > p');
                const authorNode = card.querySelector('.card_t .card_tm > div');
                const authorCopy = authorNode ? authorNode.cloneNode(true) : null;
                if (authorCopy) {
                    authorCopy.querySelectorAll('span, img').forEach(function(node) {
                        node.remove();
                    });
                }
                return {
                    title: normalizeText(
                        card.getAttribute('title') ||
                        (titleNode && titleNode.textContent) ||
                        (card.classList && card.classList.contains('index_news_item') && card.querySelector('p') && card.querySelector('p').textContent) ||
                        ''
                    ),
                    author: authorCopy
                        ? normalizeText(authorCopy.textContent || '')
                        : ''
                };
            };

            const findPostModel = function(card) {
                if (!card) return null;
                const directTopicId = readTopicIdFromCard(card);
                if (directTopicId) return { topic_id: directTopicId };
                const cardText = readCardText(card);
                const title = cardText.title;
                const author = cardText.author;
                const allowRelaxedTitle = card.classList &&
                    card.classList.contains('index_news_item');
                let node = card;
                const visited = [];
                while (node) {
                    let vm = node.__vue__;
                    let depth = 0;
                    while (vm && depth < 10) {
                        if (visited.indexOf(vm) >= 0) break;
                        visited.push(vm);
                        const candidates = [];
                        collectTopicModels(vm.${'$'}data || vm, candidates, [], 0);
                        const match = candidates.find(function(item) {
                            if (!item || !item.topic_id) return false;
                            if (title && normalizeText(item.title || '') !== title) return false;
                            return !author ||
                                normalizeText(item.user_nick_name || '') === author;
                        }) || (allowRelaxedTitle ? candidates.find(function(item) {
                            if (!item || !item.topic_id || !title) return false;
                            const itemTitle = normalizeText(item.title || '');
                            return itemTitle === title ||
                                itemTitle.indexOf(title) >= 0 ||
                                title.indexOf(itemTitle) >= 0;
                        }) : null);
                        if (match) return match;
                        vm = vm.${'$'}parent;
                        depth++;
                    }
                    node = node.parentElement;
                }
                return null;
            };

            const shouldIgnore = function(target, card) {
                if (!target || !target.closest) return true;
                if (target.closest('.the_box, .van-button, button, .van-popover, .van-popup')) {
                    return true;
                }
                const cardButton = target.closest('.card_b_item');
                if (cardButton) {
                    const cardBar = cardButton.parentElement;
                    const items = cardBar
                        ? Array.from(cardBar.children).filter(function(item) {
                            return item.classList && item.classList.contains('card_b_item');
                        })
                        : [];
                    if (items.length >= 2 && cardButton === items[items.length - 1]) {
                        return true;
                    }
                }
                return !card || !card.contains(target);
            };

            const findPinnedNewsRow = function(target) {
                const row = target && target.closest ? target.closest('.index_news_item') : null;
                if (!row) return null;
                const container = row.closest('.index_news');
                return container && container.contains(row) ? row : null;
            };

            const openPostInTabletPane = function(event, post) {
                if (!post || !post.topic_id) return false;
                event.preventDefault();
                event.stopPropagation();
                event.stopImmediatePropagation();
                bridge.openTabletThreadInfo(String(post.topic_id));
                return true;
            };

            document.addEventListener('click', function(event) {
                const target = event.target;
                if (!target || !target.closest) return;
                const pinnedNewsRow = findPinnedNewsRow(target);
                if (pinnedNewsRow) {
                    const pinnedPost = findPostModel(pinnedNewsRow);
                    if (openPostInTabletPane(event, pinnedPost)) return;
                }
                const card = target.closest('.card_item');
                if (shouldIgnore(target, card)) return;
                const activeArea = target.closest('.card_t, .card_m, .card_b_item, .img_box');
                if (!activeArea || !card.contains(activeArea)) return;
                const post = findPostModel(card);
                if (!openPostInTabletPane(event, post)) {
                    window.__androidTabletThreadSplitLastFailure = {
                        title: readCardText(card).title,
                        time: Date.now()
                    };
                }
            }, true);
            return true;
        })();
        """.trimIndent()
    }

    fun clickWebNavItem(webIndex: Int): String =
        """
        (function() {
            // 消息页自身也有 .nav；必须精确选择 home 根组件的五项底栏。
            const nav = document.querySelector('.home_main > .nav');
            if (!nav) return false;
            const item = Array.from(nav.children)[$webIndex - 1];
            if (!item) return false;
            item.click();
            return true;
        })();
        """.trimIndent()
}
