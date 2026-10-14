"""Regression tests for safe login headers in the real-stack test client."""
import io
import unittest
from unittest.mock import patch
import client


class Response(io.BytesIO):
    status = 200


class LoginHeadersTest(unittest.TestCase):
    def invoke(self, path, token):
        with patch("client.urllib.request.urlopen", return_value=Response(b'{"ok":true}')) as opened:
            client.request(path, token, {"username": "trader", "password": "test-only"})
            return opened.call_args.args[0]

    def test_login_omits_empty_bearer_header(self):
        self.assertIsNone(self.invoke("/auth/login", "").get_header("Authorization"))

    def test_login_omits_stale_bearer_header(self):
        self.assertIsNone(self.invoke("/auth/login", "expired").get_header("Authorization"))

    def test_authenticated_requests_keep_bearer_token(self):
        self.assertEqual("Bearer access", self.invoke("/orders", "access").get_header("Authorization"))


if __name__ == "__main__":
    unittest.main()
