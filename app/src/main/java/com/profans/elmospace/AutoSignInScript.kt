package com.profans.elmospace

import com.profans.elmospace.WebConstants.SIGN_IN_URL
import com.profans.elmospace.WebConstants.SIGN_STATUS_URL

object AutoSignInScript {
    const val RESULT_SUCCESS = "success"
    const val RESULT_ALREADY_DONE = "already_done"
    const val RESULT_NOT_LOGGED_IN = "not_logged_in"
    const val RESULT_FAILED = "failed"

    fun build(bridgeName: String, callbackName: String): String =
        """
        (function() {
            const bridge = window.$bridgeName;
            const token = localStorage.getItem('key');
            if (!bridge || !token) {
                if (bridge) bridge.$callbackName('$RESULT_NOT_LOGGED_IN');
                return;
            }

            const headers = {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'Authorization': token
            };
            const controller = new AbortController();
            const timeoutId = setTimeout(function() { controller.abort(); }, 15000);
            let finished = false;
            const finish = function(result) {
                if (finished) return;
                finished = true;
                clearTimeout(timeoutId);
                bridge.$callbackName(result);
            };
            const parseResponse = function(response) {
                if (!response.ok) throw new Error('http_' + response.status);
                return response.json();
            };
            const isSuccess = function(body) {
                return body && String(body.Code) === '0';
            };

            fetch('$SIGN_STATUS_URL', {
                method: 'GET', headers: headers, signal: controller.signal
            })
            .then(parseResponse)
            .then(function(statusBody) {
                if (!isSuccess(statusBody)) throw new Error('status_failed');
                if (statusBody.data && statusBody.data.has_sign_in) {
                    finish('$RESULT_ALREADY_DONE');
                    return null;
                }
                return fetch('$SIGN_IN_URL', {
                    method: 'POST', headers: headers, body: '{}', signal: controller.signal
                });
            })
            .then(function(signResponse) {
                return signResponse ? parseResponse(signResponse) : null;
            })
            .then(function(signBody) {
                if (signBody) finish(isSuccess(signBody) ? '$RESULT_SUCCESS' : '$RESULT_FAILED');
            })
            .catch(function() { finish('$RESULT_FAILED'); });
        })();
        """.trimIndent()
}
