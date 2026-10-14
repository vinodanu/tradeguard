import { useState, useEffect, type ReactNode } from "react";
import {
  Routes,
  Route,
  NavLink,
  useNavigate,
  useParams,
} from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  MenuItem,
  Paper,
  TextField,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  Tooltip,
} from "@mui/material";
import {
  ShieldOutlined,
  DashboardOutlined,
  SwapHorizOutlined,
  PolicyOutlined,
  RuleOutlined,
  ScienceOutlined,
  LogoutOutlined,
  ArrowForwardOutlined,
  AddOutlined,
  CheckCircleOutline,
  VisibilityOutlined,
} from "@mui/icons-material";
import { api, setToken, stream, type Row } from "./api";
type Session = { user: string; role: string };
const money = (value: string | number) =>
  new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  }).format(Number(value));
const short = (id: string) => id?.slice(0, 8) ?? "—";
function Status({ value }: { value: string }) {
  return (
    <Chip
      size="small"
      label={value?.replaceAll("_", " ")}
      className={`status status-${value}`}
    />
  );
}
function Header({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <div className="eyebrow">{eyebrow}</div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action}
    </div>
  );
}
function Loading() {
  return (
    <div className="loading">
      <CircularProgress size={24} />
      <span>Loading workspace…</span>
    </div>
  );
}
function Empty({ text }: { text: string }) {
  return (
    <div className="empty">
      <VisibilityOutlined />
      <h3>Nothing here yet</h3>
      <p>{text}</p>
    </div>
  );
}
function ErrorView({ error }: { error: Error | null }) {
  return error ? (
    <Alert severity="error" sx={{ mb: 2 }}>
      {error.message}
    </Alert>
  ) : null;
}
export default function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [connected, setConnected] = useState(false);
  const cache = useQueryClient();
  useEffect(() => {
    const expire = () => {
      setToken("");
      setSession(null);
      cache.clear();
    };
    window.addEventListener("tradeguard-expired", expire);
    return () => window.removeEventListener("tradeguard-expired", expire);
  }, [cache]);
  useEffect(() => {
    if (!session) return;
    const controller = new AbortController();
    let pending: ReturnType<typeof setTimeout> | undefined;
    void stream(
      controller.signal,
      () => {
        if (!pending)
          pending = setTimeout(() => {
            void cache.invalidateQueries();
            pending = undefined;
          }, 250);
      },
      setConnected,
    );
    return () => {
      controller.abort();
      if (pending) clearTimeout(pending);
    };
  }, [session, cache]);
  if (!session) return <Login onLogin={setSession} />;
  const links = [
    ["/", "Overview", <DashboardOutlined />],
    ["/orders", "Orders", <SwapHorizOutlined />],
    ["/cases", "Investigations", <PolicyOutlined />],
    ["/rules", "Rule management", <RuleOutlined />],
    ["/operations", "Demo operations", <ScienceOutlined />],
  ];
  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">
            <ShieldOutlined />
          </div>
          <div>
            TradeGuard<span>COMPLIANCE WORKSPACE</span>
          </div>
        </div>
        <div className="nav-label">WORKSPACE</div>
        <nav>
          {links.map(([url, label, icon]) => (
            <NavLink key={String(url)} to={String(url)} end={url === "/"}>
              {icon}
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="simulation">
            <span className="dot" />
            Simulation environment
          </div>
          <p>Synthetic instruments. Explainable decisions.</p>
          <div className="profile">
            <div className="avatar">
              {session.user.slice(0, 1).toUpperCase()}
            </div>
            <div>
              {session.user}
              <small>{session.role.toLowerCase()}</small>
            </div>
            <Tooltip title="Sign out">
              <Button
                aria-label="Sign out"
                onClick={() => {
                  setToken("");
                  setSession(null);
                  cache.clear();
                }}
              >
                <LogoutOutlined />
              </Button>
            </Tooltip>
          </div>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span>
            Portfolio compliance <span className="slash">/</span> TradeGuard
          </span>
          <div className="topbar-right">
            <span className={`dot ${connected ? "" : "offline"}`} />
            {connected ? "Live updates connected" : "Reconnecting live updates"}
            <Chip size="small" label="DEMO" />
          </div>
        </header>
        <main>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/orders" element={<Orders session={session} />} />
            <Route
              path="/orders/:id"
              element={<OrderDetail session={session} />}
            />
            <Route path="/evaluations/:id" element={<Decision />} />
            <Route path="/cases" element={<Cases session={session} />} />
            <Route path="/rules" element={<Rules session={session} />} />
            <Route
              path="/operations"
              element={<Operations session={session} />}
            />
          </Routes>
        </main>
        <footer>
          TradeGuard · Synthetic trading simulation{" "}
          <span>Dashboard views are eventually consistent.</span>
        </footer>
      </div>
    </div>
  );
}
function Login({ onLogin }: { onLogin: (s: Session) => void }) {
  const [username, setUsername] = useState("trader"),
    [password, setPassword] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  return (
    <div className="login">
      <div className="login-story">
        <div className="brand">
          <ShieldOutlined />
          TradeGuard
        </div>
        <div className="eyebrow">A CLEARER VIEW OF COMPLIANCE</div>
        <h1>
          Every trade.
          <br />
          Every decision.
          <br />
          <em>Explained.</em>
        </h1>
        <p>
          Follow an order from pre-trade checks to post-trade investigation. See
          the rules, the relationships, and the reasoning behind every outcome.
        </p>
        <div className="login-tags">
          <span>Spring Boot</span>
          <span>Event-driven</span>
          <span>Live workspace</span>
        </div>
      </div>
      <Paper className="login-card">
        <div className="eyebrow">SIMULATION WORKSPACE</div>
        <h2>Welcome to TradeGuard</h2>
        <p>Sign in with a seeded demo account.</p>
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            setBusy(true);
            setError("");
            try {
              const r = await api("/auth/login", { username, password });
              setToken(r.accessToken);
              onLogin({ user: r.user, role: r.role });
            } catch (e) {
              setError((e as Error).message);
            } finally {
              setBusy(false);
            }
          }}
        >
          <TextField
            fullWidth
            select
            label="Demo account"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          >
            {["trader", "trader2", "reviewer", "admin", "viewer"].map((u) => (
              <MenuItem key={u} value={u}>
                {u}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            fullWidth
            required
            label="Demo password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {error && <Alert severity="error">{error}</Alert>}
          <Button
            type="submit"
            fullWidth
            variant="contained"
            size="large"
            disabled={busy}
            endIcon={<ArrowForwardOutlined />}
          >
            {busy ? "Signing in…" : "Open workspace"}
          </Button>
        </form>
        <small>
          The password is generated by the bootstrap script. All trades and
          instruments are synthetic.
        </small>
      </Paper>
    </div>
  );
}
function Dashboard() {
  const {
    data: d,
    isPending,
    error,
  } = useQuery({ queryKey: ["dashboard"], queryFn: () => api("/dashboard") });
  const navigate = useNavigate();
  return (
    <>
      <Header
        eyebrow="COMPLIANCE AT A GLANCE"
        title="Portfolio overview"
        description="From order submission to investigation, keep every decision in view."
        action={
          <Button
            variant="contained"
            onClick={() => navigate("/orders")}
            endIcon={<ArrowForwardOutlined />}
          >
            Open order workspace
          </Button>
        }
      />
      <ErrorView error={error} />
      {isPending ? (
        <Loading />
      ) : (
        d && (
          <>
            <div className="metrics">
              {[
                [
                  "Total orders",
                  d.orders,
                  "Across accessible portfolios",
                  "neutral",
                ],
                [
                  "Ready to trade",
                  d.statuses.READY,
                  "Checks complete or review approved",
                  "green",
                ],
                [
                  "Blocked orders",
                  d.statuses.BLOCKED,
                  "Restrictions require action",
                  "red",
                ],
                [
                  "Open investigations",
                  d.openCases,
                  "Awaiting reviewer resolution",
                  "amber",
                ],
              ].map(([label, value, caption, color]) => (
                <Paper className={`metric ${color}`} key={String(label)}>
                  <div className="metric-label">{label}</div>
                  <strong>{value}</strong>
                  <small>{caption}</small>
                </Paper>
              ))}
            </div>
            <div className="dashboard-grid">
              <Paper className="panel">
                <div className="panel-head">
                  <div>
                    <h2>Activity timeline</h2>
                    <p>Durable notifications from the event pipeline</p>
                  </div>
                  <span className="live-pill">LIVE FEED</span>
                </div>
                {d.recentActivity.length === 0 ? (
                  <Empty text="Submit an order to begin the event timeline." />
                ) : (
                  d.recentActivity.map((a: Row) => (
                    <div className="activity" key={a.id}>
                      <div className="activity-icon">
                        <CheckCircleOutline />
                      </div>
                      <div>
                        <strong>
                          {a.eventType.replace(/([a-z])([A-Z])/g, "$1 $2")}
                        </strong>
                        <span>
                          {short(a.aggregateId)} ·{" "}
                          {a.portfolioId || "Configuration"}
                        </span>
                      </div>
                      <time>{new Date(a.occurredAt).toLocaleTimeString()}</time>
                    </div>
                  ))
                )}
              </Paper>
              <div>
                <Paper className="panel flow-panel">
                  <div className="eyebrow">THE DECISION PATH</div>
                  <h2>A traceable workflow</h2>
                  {[
                    ["01", "Submit", "Immutable order input"],
                    [
                      "02",
                      "Evaluate",
                      "Versioned rules + instrument enrichment",
                    ],
                    ["03", "Review", "Human decisions with an audit trail"],
                    [
                      "04",
                      "Execute & monitor",
                      "Post-trade checks and investigations",
                    ],
                  ].map(([n, title, body]) => (
                    <div className="flow-step" key={n}>
                      <b>{n}</b>
                      <div>
                        <strong>{title}</strong>
                        <p>{body}</p>
                      </div>
                    </div>
                  ))}
                </Paper>
                <Alert severity="info" sx={{ mt: 2 }}>
                  This workspace uses synthetic data. A decision represents the
                  configuration snapshot recorded in its evaluation.
                </Alert>
              </div>
            </div>
          </>
        )
      )}
    </>
  );
}
function Orders({ session }: { session: Session }) {
  const [open, setOpen] = useState(false),
    [status, setStatus] = useState(""),
    [error, setError] = useState<Error | null>(null);
  const [page, setPage] = useState(0);
  const navigate = useNavigate();
  const {
    data,
    isPending,
    error: queryError,
  } = useQuery({
    queryKey: ["orders", status, page],
    queryFn: () => api(`/orders?status=${status}&page=${page}`),
  });
  return (
    <>
      <Header
        eyebrow="PRE-TRADE WORKSPACE"
        title="Orders"
        description="Submit an order, inspect its decision, and simulate execution."
        action={
          ["TRADER", "ADMIN"].includes(session.role) && (
            <Button
              startIcon={<AddOutlined />}
              variant="contained"
              onClick={() => setOpen(true)}
            >
              New order
            </Button>
          )
        }
      />
      <ErrorView error={error || queryError} />
      <Paper className="panel">
        <div className="panel-head">
          <h2>Order book</h2>
          <TextField
            select
            label="Status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
            sx={{ minWidth: 190 }}
          >
            <MenuItem value="">All statuses</MenuItem>
            {[
              "EVALUATING",
              "READY",
              "BLOCKED",
              "AWAITING_REVIEW",
              "REJECTED",
              "EXECUTED",
            ].map((s) => (
              <MenuItem key={s} value={s}>
                {s.replaceAll("_", " ")}
              </MenuItem>
            ))}
          </TextField>
        </div>
        {isPending ? (
          <Loading />
        ) : !data?.items.length ? (
          <Empty text="Create an ACME order to see an allow decision, or FUND_X to explore an indirect restriction." />
        ) : (
          <div className="table-wrap">
            <Table>
              <TableHead>
                <TableRow>
                  {[
                    "Order",
                    "Instrument",
                    "Side",
                    "Notional",
                    "Status",
                    "Submitted",
                    "",
                  ].map((x) => (
                    <TableCell key={x}>{x}</TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {data.items.map((r: Row) => (
                  <TableRow hover key={r.id}>
                    <TableCell>
                      <code>{short(r.id)}</code>
                      <small className="cell-sub">{r.portfolioId}</small>
                    </TableCell>
                    <TableCell>
                      <b>{r.instrumentId}</b>
                    </TableCell>
                    <TableCell>{r.side}</TableCell>
                    <TableCell>
                      {money(Number(r.quantity) * Number(r.price))}
                    </TableCell>
                    <TableCell>
                      <Status value={r.status} />
                    </TableCell>
                    <TableCell>
                      {new Date(r.createdAt).toLocaleString()}
                    </TableCell>
                    <TableCell>
                      <Button onClick={() => navigate(`/orders/${r.id}`)}>
                        View <ArrowForwardOutlined fontSize="small" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
        {data && <Pagination page={page} total={data.total} change={setPage} />}
      </Paper>
      <NewOrder open={open} close={() => setOpen(false)} onError={setError} />
    </>
  );
}
function NewOrder({
  open,
  close,
  onError,
}: {
  open: boolean;
  close: () => void;
  onError: (e: Error) => void;
}) {
  const cache = useQueryClient();
  const { data: portfolios } = useQuery({
    queryKey: ["portfolios"],
    queryFn: () => api<Row[]>("/portfolios"),
  });
  const [form, setForm] = useState({
    portfolioId: "PORTFOLIO_A",
    instrumentId: "ACME",
    side: "BUY",
    quantity: "10",
    price: "100",
  });
  const [busy, setBusy] = useState(false);
  const [key, setKey] = useState(crypto.randomUUID());
  useEffect(() => {
    if (portfolios?.length)
      setForm((f) => ({ ...f, portfolioId: portfolios[0].id }));
  }, [portfolios]);
  useEffect(() => setKey(crypto.randomUUID()), [form]);
  const field = (name: keyof typeof form, label: string) => (
    <TextField
      fullWidth
      label={label}
      value={form[name]}
      onChange={(e) => setForm({ ...form, [name]: e.target.value })}
    />
  );
  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="sm">
      <DialogTitle>Submit a simulated order</DialogTitle>
      <DialogContent className="form-stack">
        <TextField
          fullWidth
          select
          label="Portfolio"
          value={form.portfolioId}
          onChange={(e) => setForm({ ...form, portfolioId: e.target.value })}
        >
          {portfolios?.map((p) => (
            <MenuItem key={p.id} value={p.id}>
              {p.name}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          fullWidth
          select
          label="Instrument"
          value={form.instrumentId}
          onChange={(e) => setForm({ ...form, instrumentId: e.target.value })}
        >
          {[
            "ACME",
            "NOVA",
            "BANNED",
            "FUND_X",
            "CYCLE_A",
            "MISSING_REF",
            "UNKNOWN",
          ].map((i) => (
            <MenuItem key={i} value={i}>
              {i}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          fullWidth
          select
          label="Side"
          value={form.side}
          onChange={(e) => setForm({ ...form, side: e.target.value })}
        >
          <MenuItem value="BUY">BUY</MenuItem>
          <MenuItem value="SELL">SELL</MenuItem>
        </TextField>
        <div className="two-col">
          {field("quantity", "Quantity")}
          {field("price", "Price (USD)")}
        </div>
        <Alert severity="info">
          Notional: {money(Number(form.quantity) * Number(form.price))}. Orders
          above $100,000 require review under the initial ruleset.
        </Alert>
      </DialogContent>
      <DialogActions>
        <Button onClick={close}>Cancel</Button>
        <Button
          variant="contained"
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            try {
              await api("/orders", form, key);
              await cache.invalidateQueries();
              close();
            } catch (e) {
              onError(e as Error);
            } finally {
              setBusy(false);
            }
          }}
        >
          Submit order
        </Button>
      </DialogActions>
    </Dialog>
  );
}
function OrderDetail({ session }: { session: Session }) {
  const { id } = useParams();
  const navigate = useNavigate();
  const cache = useQueryClient();
  const [error, setError] = useState<Error | null>(null);
  const [busy, setBusy] = useState(false);
  const [executeKey, setExecuteKey] = useState(crypto.randomUUID());
  const {
    data: d,
    isPending,
    error: queryError,
  } = useQuery({
    queryKey: ["order", id],
    queryFn: () => api(`/orders/${id}`),
  });
  return (
    <>
      <Header
        eyebrow="AUTHORITATIVE ORDER RECORD"
        title={`Order ${short(id!)}`}
        description="Execution uses the current decision configuration; published changes trigger fresh evaluation."
      />
      <ErrorView error={error || queryError} />
      {isPending ? (
        <Loading />
      ) : (
        d && (
          <>
            <Paper className="panel detail-panel">
              <div className="panel-head">
                <h2>
                  {d.instrumentId} <span className="muted">/ {d.side}</span>
                </h2>
                <Status value={d.status} />
              </div>
              <div className="detail-grid">
                {[
                  ["Portfolio", d.portfolioId],
                  ["Quantity", d.quantity],
                  ["Price", money(d.price)],
                  ["Notional", money(Number(d.quantity) * Number(d.price))],
                  ["Trader", d.traderId],
                  ["Evaluation attempt", d.attempt],
                ].map(([k, v]) => (
                  <div key={k}>
                    <small>{k}</small>
                    <strong>{v}</strong>
                  </div>
                ))}
              </div>
              <div className="actions">
                {d.evaluationId && (
                  <Button
                    variant="outlined"
                    onClick={() => navigate(`/evaluations/${d.evaluationId}`)}
                  >
                    Inspect decision
                  </Button>
                )}
                {d.status === "READY" &&
                  ["TRADER", "ADMIN"].includes(session.role) && (
                    <Button
                      disabled={busy}
                      variant="contained"
                      onClick={async () => {
                        setBusy(true);
                        try {
                          await api(
                            `/orders/${id}/execute`,
                            { expectedVersion: d.version },
                            executeKey,
                          );
                          setExecuteKey(crypto.randomUUID());
                          await cache.invalidateQueries();
                        } catch (e) {
                          setError(e as Error);
                        } finally {
                          setBusy(false);
                        }
                      }}
                    >
                      Simulate execution
                    </Button>
                  )}
              </div>
            </Paper>
            <Paper className="panel">
              <div className="panel-head">
                <h2>Audit history</h2>
              </div>
              {d.audit.map((a: Row) => (
                <div className="activity" key={a.id}>
                  <div className="activity-icon">
                    <ShieldOutlined />
                  </div>
                  <div>
                    <strong>{a.action.replaceAll("_", " ")}</strong>
                    <span>{a.actor}</span>
                  </div>
                  <time>{new Date(a.at).toLocaleString()}</time>
                </div>
              ))}
            </Paper>
          </>
        )
      )}
    </>
  );
}
function Decision() {
  const { id } = useParams();
  const {
    data: d,
    isPending,
    error,
  } = useQuery({
    queryKey: ["evaluation", id],
    queryFn: () => api(`/evaluations/${id}`),
  });
  return (
    <>
      <Header
        eyebrow="EXPLAINABLE COMPLIANCE"
        title="Decision detail"
        description="The original input and configuration remain attached to this evaluation."
      />
      <ErrorView error={error} />
      {isPending ? (
        <Loading />
      ) : (
        d && (
          <>
            <Paper className="panel detail-panel">
              <div className="panel-head">
                <h2>
                  {d.input.instrumentId} · {d.workflow.replaceAll("_", " ")}
                </h2>
                <Status value={d.outcome} />
              </div>
              <div className="detail-grid">
                {[
                  ["Ruleset", d.rulesetId || "Unavailable"],
                  ["Dataset", d.datasetId || "Unavailable"],
                  ["Engine", d.engineVersion],
                  ["Selected at", new Date(d.selectedAt).toLocaleString()],
                ].map(([k, v]) => (
                  <div key={k}>
                    <small>{k}</small>
                    <strong>{v}</strong>
                  </div>
                ))}
              </div>
              {d.reasons?.map((r: Row, i: number) => (
                <Alert
                  key={i}
                  severity={
                    r.severity === "BLOCK"
                      ? "error"
                      : r.severity === "REVIEW"
                        ? "warning"
                        : "success"
                  }
                  sx={{ my: 1 }}
                >
                  <b>{r.code}</b>
                  <br />
                  {r.message}
                </Alert>
              ))}
            </Paper>
            <Paper className="panel">
              <div className="panel-head">
                <h2>Instrument enrichment</h2>
              </div>
              {d.paths?.map((p: Row, i: number) => (
                <div className="relationship" key={i}>
                  <code>{p.path.join(" → ")}</code>
                  <span>{p.issuer || p.error}</span>
                </div>
              ))}
            </Paper>
            <Paper className="panel">
              <div className="panel-head">
                <h2>Recorded input</h2>
              </div>
              <pre className="json">{JSON.stringify(d.input, null, 2)}</pre>
            </Paper>
          </>
        )
      )}
    </>
  );
}
function Cases({ session }: { session: Session }) {
  const [page, setPage] = useState(0);
  const cache = useQueryClient(),
    navigate = useNavigate();
  const [selected, setSelected] = useState(""),
    [note, setNote] = useState(""),
    [disposition, setDisposition] = useState(""),
    [error, setError] = useState<Error | null>(null),
    [busy, setBusy] = useState(false);
  const {
    data,
    isPending,
    error: queryError,
  } = useQuery({
    queryKey: ["cases", page],
    queryFn: () => api(`/cases?page=${page}`),
  });
  const { data: detail } = useQuery({
    queryKey: ["case", selected],
    queryFn: () => api(`/cases/${selected}`),
    enabled: !!selected,
  });
  const canReview = ["REVIEWER", "ADMIN"].includes(session.role);
  const act = async (action: string, body: Row) => {
    setBusy(true);
    try {
      await api(`/cases/${selected}/${action}`, body);
      setNote("");
      await cache.invalidateQueries();
    } catch (e) {
      setError(e as Error);
    } finally {
      setBusy(false);
    }
  };
  return (
    <>
      <Header
        eyebrow="HUMAN REVIEW & SURVEILLANCE"
        title="Investigations"
        description="Resolve flagged orders and investigate executed-trade violations with a recorded explanation."
      />
      <ErrorView error={error || queryError} />
      <Paper className="panel">
        {isPending ? (
          <Loading />
        ) : !data?.items.length ? (
          <Empty text="A review decision or post-trade violation will open an investigation here." />
        ) : (
          <div className="table-wrap">
            <Table>
              <TableHead>
                <TableRow>
                  {[
                    "Case",
                    "Workflow",
                    "Severity",
                    "Status",
                    "Assigned",
                    "",
                  ].map((x) => (
                    <TableCell key={x}>{x}</TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {data.items.map((c: Row) => (
                  <TableRow key={c.id} hover>
                    <TableCell>
                      <code>{short(c.id)}</code>
                      <small className="cell-sub">
                        {c.input?.instrumentId || c.instrumentId}
                      </small>
                    </TableCell>
                    <TableCell>{c.workflow.replaceAll("_", " ")}</TableCell>
                    <TableCell>
                      <Status value={c.severity} />
                    </TableCell>
                    <TableCell>
                      <Status value={c.status} />
                    </TableCell>
                    <TableCell>{c.assignedTo || "Unassigned"}</TableCell>
                    <TableCell>
                      <Button
                        onClick={() => {
                          setSelected(c.id);
                          setDisposition(
                            c.workflow === "PRE_TRADE"
                              ? "APPROVED"
                              : "CONFIRMED",
                          );
                        }}
                      >
                        Open case
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
        {data && <Pagination page={page} total={data.total} change={setPage} />}
      </Paper>
      <Dialog
        open={!!selected}
        onClose={() => setSelected("")}
        fullWidth
        maxWidth="md"
      >
        <DialogTitle>Investigation {short(selected)}</DialogTitle>
        <DialogContent className="form-stack">
          <ErrorView error={error} />
          {detail ? (
            <>
              <div className="actions">
                <Status value={detail.status} />
                <Status value={detail.outcome} />
                <Button
                  onClick={() => {
                    setSelected("");
                    navigate(`/evaluations/${detail.evaluationId}`);
                  }}
                >
                  Inspect original decision
                </Button>
              </div>
              {detail.reasons?.map((r: Row, i: number) => (
                <Alert key={i} severity="warning">
                  {r.message}
                </Alert>
              ))}
              {canReview && detail.status !== "RESOLVED" && (
                <>
                  <Button
                    variant="outlined"
                    disabled={busy}
                    onClick={() =>
                      act("assign", { expectedVersion: detail.version })
                    }
                  >
                    Assign to me
                  </Button>
                  <TextField
                    label="Reviewer explanation"
                    multiline
                    rows={3}
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                  />
                  <TextField
                    select
                    label="Disposition"
                    value={disposition}
                    onChange={(e) => setDisposition(e.target.value)}
                  >
                    {(detail.workflow === "PRE_TRADE"
                      ? ["APPROVED", "REJECTED"]
                      : ["CONFIRMED", "FALSE_POSITIVE", "DATA_ISSUE"]
                    ).map((s) => (
                      <MenuItem key={s} value={s}>
                        {s.replaceAll("_", " ")}
                      </MenuItem>
                    ))}
                  </TextField>
                  <div className="actions">
                    <Button
                      disabled={busy || note.trim().length < 5}
                      onClick={() =>
                        act("notes", { expectedVersion: detail.version, note })
                      }
                    >
                      Add note
                    </Button>
                    <Button
                      disabled={busy || note.trim().length < 5}
                      variant="contained"
                      onClick={() =>
                        act("resolve", {
                          expectedVersion: detail.version,
                          note,
                          disposition,
                        })
                      }
                    >
                      Resolve case
                    </Button>
                  </div>
                </>
              )}
              {detail.resolutionNote && (
                <Alert severity="success">
                  {detail.disposition}: {detail.resolutionNote}
                </Alert>
              )}
              <h3>Case history</h3>
              {detail.actions?.map((a: Row) => (
                <p key={a.id}>
                  <b>{a.action}</b> · {a.actor} · {a.note}
                </p>
              ))}
              {detail.notes?.map((n: Row) => (
                <p key={n.id}>
                  <b>{n.actor}</b> · {n.note}
                </p>
              ))}
            </>
          ) : (
            <Loading />
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSelected("")}>Close</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
function Rules({ session }: { session: Session }) {
  const cache = useQueryClient();
  const [comparison, setComparison] = useState<Row | null>(null);
  const {
    data,
    isPending,
    error: queryError,
  } = useQuery({ queryKey: ["rules"], queryFn: () => api("/rulesets") });
  const [error, setError] = useState<Error | null>(null),
    [busy, setBusy] = useState(false);
  const [form, setForm] = useState({
    name: "Updated restrictions",
    restrictedInstruments: "BANNED",
    restrictedIssuers: "BANNED_CORP",
    maxNotional: "100000",
  });
  return (
    <>
      <Header
        eyebrow="VERSIONED CONFIGURATION"
        title="Rule management"
        description="Publish a new revision. Existing decisions retain their original rules and input."
      />
      <ErrorView error={error || queryError} />
      {isPending ? (
        <Loading />
      ) : (
        data && (
          <>
            <Alert severity="info" sx={{ mb: 2 }}>
              Active ruleset: {data.active.rulesetId} · Configuration revision{" "}
              {data.active.version}
            </Alert>
            <Paper className="panel">
              <div className="panel-head">
                <h2>Ruleset history</h2>
              </div>
              {data.items.map((r: Row) => (
                <div className="rule-row" key={r.id}>
                  <div>
                    <strong>{r.name}</strong>
                    <p>
                      {r.restrictedInstruments.join(", ") ||
                        "No instrument restrictions"}{" "}
                      · Review above {money(r.maxNotional)}
                    </p>
                    <code>{r.id}</code>
                  </div>
                  <div className="actions">
                    <Status value={r.published ? "PUBLISHED" : "DRAFT"} />
                    <Button onClick={() => setComparison(r)}>
                      Compare with active
                    </Button>
                    {session.role === "ADMIN" && !r.published && (
                      <Button
                        variant="outlined"
                        disabled={busy}
                        onClick={async () => {
                          setBusy(true);
                          try {
                            await api(`/rulesets/${r.id}/publish`, {
                              expectedVersion: data.active.version,
                            });
                            await cache.invalidateQueries();
                          } catch (e) {
                            setError(e as Error);
                          } finally {
                            setBusy(false);
                          }
                        }}
                      >
                        Publish revision
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </Paper>
            {session.role === "ADMIN" && (
              <Paper className="panel detail-panel">
                <h2>Create a draft</h2>
                <div className="form-stack">
                  {Object.entries(form).map(([key, value]) => (
                    <TextField
                      key={key}
                      label={
                        {
                          name: "Ruleset name",
                          restrictedInstruments:
                            "Restricted instruments (comma separated)",
                          restrictedIssuers:
                            "Restricted issuers (comma separated)",
                          maxNotional: "Maximum notional (USD)",
                        }[key]
                      }
                      value={value}
                      onChange={(e) =>
                        setForm({ ...form, [key]: e.target.value })
                      }
                    />
                  ))}
                  <Button
                    variant="contained"
                    disabled={busy}
                    onClick={async () => {
                      setBusy(true);
                      try {
                        await api("/rulesets", {
                          ...form,
                          restrictedInstruments: form.restrictedInstruments
                            .split(",")
                            .map((s) => s.trim())
                            .filter(Boolean),
                          restrictedIssuers: form.restrictedIssuers
                            .split(",")
                            .map((s) => s.trim())
                            .filter(Boolean),
                        });
                        await cache.invalidateQueries();
                      } catch (e) {
                        setError(e as Error);
                      } finally {
                        setBusy(false);
                      }
                    }}
                  >
                    Save draft revision
                  </Button>
                </div>
              </Paper>
            )}
          </>
        )
      )}
      <Dialog
        open={!!comparison}
        onClose={() => setComparison(null)}
        fullWidth
        maxWidth="md"
      >
        <DialogTitle>Compare rule revisions</DialogTitle>
        <DialogContent>
          {comparison && data && (
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Rule</TableCell>
                  <TableCell>Active configuration</TableCell>
                  <TableCell>Selected revision</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {[
                  "restrictedInstruments",
                  "restrictedIssuers",
                  "maxNotional",
                ].map((field) => {
                  const active = data.items.find(
                    (r: Row) => r.id === data.active.rulesetId,
                  );
                  const value = (r: Row | undefined) =>
                    Array.isArray(r?.[field])
                      ? r?.[field].join(", ") || "None"
                      : r?.[field] || "Unavailable";
                  return (
                    <TableRow key={field}>
                      <TableCell>
                        {field === "maxNotional"
                          ? "Maximum notional (USD)"
                          : field === "restrictedIssuers"
                            ? "Restricted issuers"
                            : "Restricted instruments"}
                      </TableCell>
                      <TableCell>{value(active)}</TableCell>
                      <TableCell>{value(comparison)}</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setComparison(null)}>Close</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
function Operations({ session }: { session: Session }) {
  const [csv, setCsv] = useState(
    "source,externalTradeId,portfolioId,instrumentId,side,quantity,price,executedAt\ndemo,trade-001,PORTFOLIO_A,BANNED,BUY,10,100,2025-01-01T12:00:00Z",
  );
  const [result, setResult] = useState<Row | null>(null),
    [error, setError] = useState<Error | null>(null),
    [busy, setBusy] = useState(false);
  return (
    <>
      <Header
        eyebrow="REPRODUCIBLE SCENARIOS"
        title="Demo operations"
        description="Use synthetic fixtures to demonstrate decisions, investigations, and recovery."
      />
      <div className="scenario-grid">
        {[
          ["ACME", "Allow", "A normal order passes the initial restrictions."],
          ["FUND_X", "Block", "An underlying holding links to BANNED."],
          [
            "ACME · $100,001",
            "Review",
            "Notional exceeds the initial review threshold.",
          ],
          ["CYCLE_A", "Data quality", "A cycle creates a review outcome."],
        ].map(([name, type, text]) => (
          <Paper className="panel scenario" key={name}>
            <div className="eyebrow">{type}</div>
            <h2>{name}</h2>
            <p>{text}</p>
          </Paper>
        ))}
      </div>
      {session.role === "ADMIN" ? (
        <Paper className="panel detail-panel">
          <h2>Import historical executions</h2>
          <p>
            All rows are validated before acceptance. Maximum 100 rows; unquoted
            CSV fields only.
          </p>
          <TextField
            fullWidth
            multiline
            rows={7}
            value={csv}
            onChange={(e) => setCsv(e.target.value)}
            label="Synthetic execution CSV"
          />
          <ErrorView error={error} />
          {result && (
            <Alert severity="success">
              Accepted {result.accepted} executions. Matching violations will
              appear in Investigations.
            </Alert>
          )}
          <Button
            variant="contained"
            disabled={busy}
            sx={{ mt: 2 }}
            onClick={async () => {
              setBusy(true);
              setError(null);
              try {
                setResult(await api("/executions/import", { csv }));
              } catch (e) {
                setError(e as Error);
              } finally {
                setBusy(false);
              }
            }}
          >
            Import and evaluate
          </Button>
        </Paper>
      ) : (
        <Alert severity="info">
          Sign in as admin to import historical executions. Use the documented
          recovery scripts for broker outages, consumer restarts, replay, and
          backup restoration.
        </Alert>
      )}
      <Paper className="panel detail-panel">
        <h2>Failure recovery</h2>
        <p>
          Inspect failed events and rebuild dashboard views from the durable
          event feed. Broker outage and backup demonstrations run from the
          owner-controlled local environment.
        </p>
        {session.role === "ADMIN" && <RecoveryTools />}
      </Paper>
    </>
  );
}

function RecoveryTools() {
  const cache = useQueryClient();
  const [error, setError] = useState<Error | null>(null),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false);
  const { data, error: queryError } = useQuery({
    queryKey: ["dead-letters"],
    queryFn: () => api<Row[]>("/operations/dead-letters"),
  });
  const act = async (path: string) => {
    setBusy(true);
    setError(null);
    try {
      const result = await api(path, {});
      setMessage(
        result.generation
          ? "Rebuilt dashboard generation " + result.generation.slice(0, 8)
          : "Replay queued; the original event ID is preserved.",
      );
      await cache.invalidateQueries();
    } catch (e) {
      setError(e as Error);
    } finally {
      setBusy(false);
    }
  };
  return (
    <>
      <ErrorView error={error || queryError} />
      {message && (
        <Alert severity="success" sx={{ my: 2 }}>
          {message}
        </Alert>
      )}
      <Button
        variant="outlined"
        disabled={busy}
        onClick={() => act("/operations/projections/rebuild")}
      >
        Rebuild dashboard projections
      </Button>
      <h3 style={{ marginTop: 25 }}>Dead-letter events</h3>
      {data?.length ? (
        data.map((d) => (
          <div className="rule-row" key={d.id}>
            <div>
              <strong>{d.topic}</strong>
              <p>
                Partition {d.partition} � Offset {d.offset} � Replays{" "}
                {d.replayCount}
              </p>
              <code>{short(d.id)}</code>
            </div>
            <Button
              disabled={busy}
              onClick={() => act(`/operations/dead-letters/${d.id}/replay`)}
            >
              Replay original event
            </Button>
          </div>
        ))
      ) : (
        <p>No failed events have been recorded.</p>
      )}
    </>
  );
}

function Pagination({
  page,
  total,
  change,
}: {
  page: number;
  total: number;
  change: (page: number) => void;
}) {
  return (
    <div className="pagination">
      <span>
        {total === 0 ? "0" : page * 20 + 1}�{Math.min((page + 1) * 20, total)}{" "}
        of {total}
      </span>
      <Button disabled={page === 0} onClick={() => change(page - 1)}>
        Previous
      </Button>
      <Button
        disabled={(page + 1) * 20 >= total}
        onClick={() => change(page + 1)}
      >
        Next
      </Button>
    </div>
  );
}
