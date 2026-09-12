#!/usr/bin/env python3
"""Minimal Source-RCON client for driving the TC-UHC dev server.

Standard library only - nothing to install.

Enable RCON in the dev server's server.properties first:

    enable-rcon=true
    rcon.port=25575
    rcon.password=tcuhc

Usage:

    python scripts/rcon.py "list"
    python scripts/rcon.py "uhc option gameMode set" "KING"
    python scripts/rcon.py --file scripts/scenarios/king-quick.txt

Commands are sent without a leading slash (RCON adds it). Each argument is one
command; they run in order and every response is printed.

Connection settings are read from, in order of precedence:
    1. --host / --port / --password flags
    2. RCON_HOST / RCON_PORT / RCON_PASSWORD environment variables
    3. the run/server.properties file, if present
    4. 127.0.0.1:25575 with an empty password
"""

from __future__ import annotations

import argparse
import os
import socket
import struct
import sys

SERVERDATA_AUTH = 3
SERVERDATA_AUTH_RESPONSE = 2
SERVERDATA_EXECCOMMAND = 2
SERVERDATA_RESPONSE_VALUE = 0

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SERVER_PROPERTIES = os.path.join(REPO_ROOT, "run", "server.properties")


class RconError(RuntimeError):
    pass


class Rcon:
    def __init__(self, host: str, port: int, password: str, timeout: float = 10.0):
        self.host = host
        self.port = port
        self.password = password
        self.timeout = timeout
        self._sock: socket.socket | None = None
        self._request_id = 0

    def __enter__(self) -> "Rcon":
        self._sock = socket.create_connection((self.host, self.port), self.timeout)
        self._sock.settimeout(self.timeout)
        request_id, _ = self._exchange(SERVERDATA_AUTH, self.password)
        if request_id == -1:
            raise RconError(
                "RCON authentication failed - check rcon.password in server.properties"
            )
        return self

    def __exit__(self, *_exc) -> None:
        if self._sock is not None:
            self._sock.close()
            self._sock = None

    def command(self, body: str) -> str:
        _, payload = self._exchange(SERVERDATA_EXECCOMMAND, body)
        return payload

    def _exchange(self, packet_type: int, body: str) -> tuple[int, str]:
        self._request_id += 1
        self._send(self._request_id, packet_type, body)
        response_id, response_type, payload = self._recv()
        # An auth failure comes back as AUTH_RESPONSE with id -1; some servers
        # send an empty RESPONSE_VALUE first, so read one more packet.
        if packet_type == SERVERDATA_AUTH and response_type == SERVERDATA_RESPONSE_VALUE:
            response_id, _response_type, payload = self._recv()
        _ = SERVERDATA_AUTH_RESPONSE
        return response_id, payload

    def _send(self, request_id: int, packet_type: int, body: str) -> None:
        assert self._sock is not None
        payload = struct.pack("<ii", request_id, packet_type) + body.encode("utf-8") + b"\x00\x00"
        self._sock.sendall(struct.pack("<i", len(payload)) + payload)

    def _recv(self) -> tuple[int, int, str]:
        (length,) = struct.unpack("<i", self._read_exact(4))
        data = self._read_exact(length)
        request_id, packet_type = struct.unpack("<ii", data[:8])
        return request_id, packet_type, data[8:-2].decode("utf-8", errors="replace")

    def _read_exact(self, count: int) -> bytes:
        assert self._sock is not None
        chunks = []
        remaining = count
        while remaining > 0:
            chunk = self._sock.recv(remaining)
            if not chunk:
                raise RconError("connection closed by the server")
            chunks.append(chunk)
            remaining -= len(chunk)
        return b"".join(chunks)


def read_server_properties() -> dict[str, str]:
    props: dict[str, str] = {}
    if not os.path.isfile(SERVER_PROPERTIES):
        return props
    with open(SERVER_PROPERTIES, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            props[key.strip()] = value.strip()
    return props


def resolve_connection(args: argparse.Namespace) -> tuple[str, int, str]:
    props = read_server_properties()
    host = args.host or os.environ.get("RCON_HOST") or "127.0.0.1"
    port = args.port or os.environ.get("RCON_PORT") or props.get("rcon.port") or "25575"
    password = (
        args.password
        if args.password is not None
        else os.environ.get("RCON_PASSWORD")
        if os.environ.get("RCON_PASSWORD") is not None
        else props.get("rcon.password", "")
    )
    return host, int(port), password


def load_commands(args: argparse.Namespace) -> list[str]:
    commands = list(args.commands)
    if args.file:
        with open(args.file, encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if line and not line.startswith("#"):
                    commands.append(line)
    return [c.lstrip("/") for c in commands]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("commands", nargs="*", help="commands to run, in order")
    parser.add_argument("--file", help="file of commands, one per line, # for comments")
    parser.add_argument("--host")
    parser.add_argument("--port", type=int)
    parser.add_argument("--password")
    parser.add_argument("--timeout", type=float, default=10.0)
    args = parser.parse_args()

    commands = load_commands(args)
    if not commands:
        parser.error("no commands given (pass them as arguments or use --file)")

    host, port, password = resolve_connection(args)

    try:
        with Rcon(host, port, password, args.timeout) as rcon:
            for command in commands:
                print(f"> {command}")
                response = rcon.command(command).strip()
                if response:
                    print(response)
                print()
    except (OSError, RconError) as exc:
        print(f"rcon: {exc}", file=sys.stderr)
        print(
            "Is the dev server running with enable-rcon=true in run/server.properties?",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
