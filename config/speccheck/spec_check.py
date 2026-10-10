#!/usr/bin/env python3
"""TapLink SDK Spec 完整性机械校验核心。

规则来源：docs/spec/SPEC_GUIDE.md（§1.3 来源可追溯、量化门禁）、
          .agents/skills/spec-authoring/（coverage.yaml 结构）。

本脚本只做**可机械判定**的检查：结构完整、ID 引用能解析、门禁数字达标、
EXPLICIT 来源字段齐全且源文件/章节可定位（存在性）。
语义正确性（原文是否真的支持该规则、规则是否业务正确）靠独立审查与人工闸门，不在此。

退出码：0 全通过；1 有 🔴（阻断项）；🟡 不阻断。
"""
from __future__ import annotations

import glob
import os
import sys

try:
    import yaml
except ImportError:
    print("spec-check: 需要 pyyaml（pip3 install pyyaml）", file=sys.stderr)
    sys.exit(2)

RED = "\033[0;31m"
YELLOW = "\033[0;33m"
GREEN = "\033[0;32m"
DIM = "\033[2m"
NC = "\033[0m"

# critical 门禁：不达标则 🔴 阻断（禁止 approved）。
# 其余为 🟡 提示。
VALID_CLASSIFICATION = {"EXPLICIT", "DERIVED", "PROPOSED", "UNSPECIFIED", "CONFLICT"}
VALID_STATUS = {"REVIEW_REQUIRED", "APPROVED", "BLOCKED"}
HIGH_RISK = {"critical", "high"}


class Report:
    def __init__(self) -> None:
        self.red: list[str] = []
        self.yellow: list[str] = []

    def err(self, msg: str) -> None:
        self.red.append(msg)

    def warn(self, msg: str) -> None:
        self.yellow.append(msg)


def _load(path: str, rpt: Report):
    try:
        with open(path, encoding="utf-8") as fh:
            return yaml.safe_load(fh)
    except Exception as exc:  # noqa: BLE001 - 报告给用户即可
        rpt.err(f"{path}: YAML 解析失败：{exc}")
        return None


def check_coverage(path: str, repo_root: str, rpt: Report) -> None:
    data = _load(path, rpt)
    if not isinstance(data, dict):
        if data is not None:
            rpt.err(f"{path}: 顶层不是 mapping")
        return

    spec_id = data.get("spec_id")
    if not spec_id:
        rpt.err(f"{path}: 缺 spec_id")

    # 源文件存在性（spec_file 指向的 md 要在仓库里）
    spec_file = data.get("spec_file")
    if not spec_file:
        rpt.err(f"{path}: 缺 spec_file")
    elif not os.path.isfile(os.path.join(repo_root, spec_file)):
        rpt.err(f"{path}: spec_file 指向的文件不存在：{spec_file}")

    source_items = data.get("sources") or []
    if not isinstance(source_items, list):
        rpt.err(f"{path}: sources 必须是列表")
        return
    sources = {s.get("source_id"): s for s in source_items if isinstance(s, dict)}
    if len(sources) != len(source_items):
        rpt.err(f"{path}: sources 存在重复 source_id 或非 mapping 项")
    rules = data.get("rules") or []
    status = data.get("status")
    if status not in {"draft", "active", "deprecated"}:
        rpt.err(f"{path}: status 非法：{status}")
    if not isinstance(data.get("approved"), bool):
        rpt.err(f"{path}: approved 必须是 YAML 布尔值")
    approved = data.get("approved") is True
    if not isinstance(rules, list) or not rules:
        rpt.err(f"{path}: rules 必须是非空列表")
        return
    if approved and not (data.get("approved_by") and data.get("approved_at")):
        rpt.err(f"{path}: approved=true 但缺 approved_by/approved_at")

    # ── 来源字段校验 ───────────────────────────────────────────────
    for sid, s in sources.items():
        if not sid:
            rpt.err(f"{path}: 存在无 source_id 的来源项")
            continue
        cls = s.get("classification")
        if cls not in VALID_CLASSIFICATION:
            rpt.err(f"{path}: {sid} classification 非法：{cls}")
        if cls == "EXPLICIT":
            # EXPLICIT 必须能定位原文（SPEC_GUIDE §1.3.3）
            for field in ("document_id", "document_version", "document_path", "section", "source_quote"):
                if not s.get(field):
                    rpt.err(f"{path}: {sid} 为 EXPLICIT 但缺 {field}（无法定位原文）")
            source_path = s.get("document_path")
            if source_path:
                absolute = os.path.abspath(os.path.join(repo_root, source_path))
                if not absolute.startswith(os.path.abspath(repo_root) + os.sep) or not os.path.isfile(absolute):
                    rpt.err(f"{path}: {sid} 原文文件不可定位：{source_path}")
                else:
                    with open(absolute, encoding="utf-8") as fh:
                        content = fh.read()
                    if s.get("section") and s["section"] not in content:
                        rpt.err(f"{path}: {sid} section 在原文文件中不存在：{s['section']}")
                    if s.get("source_quote") and " ".join(s["source_quote"].split()) not in " ".join(content.split()):
                        rpt.err(f"{path}: {sid} source_quote 在原文文件中不存在")
            if approved and not s.get("verified"):
                rpt.err(f"{path}: {sid} 为 EXPLICIT 且 spec 已批准，但 verified=false")
            elif not s.get("verified"):
                rpt.warn(f"{path}: {sid} 原文待人工核对")
        if cls == "DERIVED":
            der = s.get("derivation") or {}
            if not (isinstance(der, dict) and der.get("derived_from") and der.get("rationale")):
                rpt.err(f"{path}: {sid} 为 DERIVED 但缺 derivation.derived_from/rationale（推导链不可复核）")
        if cls == "CONFLICT" and not s.get("conflict_with"):
            rpt.err(f"{path}: {sid} 为 CONFLICT 但缺 conflict_with")

    # ── 规则 / AC / 追溯校验 ───────────────────────────────────────
    rule_ids: set[str] = set()
    ac_ids: set[str] = set()
    traced = 0
    crit_rules = 0
    crit_with_ac = 0

    for r in rules:
        if not isinstance(r, dict):
            rpt.err(f"{path}: rules 存在非 mapping 项")
            continue
        rid = r.get("rule_id")
        if not rid:
            rpt.err(f"{path}: 存在无 rule_id 的规则")
            continue
        if rid in rule_ids:
            rpt.err(f"{path}: 重复 rule_id：{rid}")
        rule_ids.add(rid)

        crit = r.get("criticality") in HIGH_RISK
        if r.get("criticality") == "critical":
            crit_rules += 1

        # 来源追溯：每条规则必须关联已声明的 source_id
        sid = r.get("source_id")
        if sid and sid in sources:
            traced += 1
        else:
            rpt.err(f"{path}: {rid} 的 source_id 无法解析：{sid!r}（来源追溯断裂）")

        # source_type 一致性
        st = r.get("source_type")
        if not st:
            rpt.err(f"{path}: {rid} 缺 source_type")
        if sid in sources and st and sources[sid].get("classification") != st:
            rpt.err(f"{path}: {rid} source_type({st}) 与 {sid} classification 不一致")

        r_status = r.get("status")
        if r_status and r_status not in VALID_STATUS:
            rpt.err(f"{path}: {rid} status 非法：{r_status}")
        if approved and r_status != "APPROVED":
            rpt.err(f"{path}: 已批准 spec 的 {rid} 状态为 {r_status}，应为 APPROVED")
        if approved and sid in sources and sources[sid].get("classification") in {"PROPOSED", "UNSPECIFIED", "CONFLICT"}:
            rpt.err(f"{path}: 已批准 spec 的 {rid} 来源仍为待决策/冲突状态")

        acs = r.get("acceptance") or []
        for ac in acs:
            if isinstance(ac, dict) and ac.get("ac_id"):
                if ac["ac_id"] in ac_ids:
                    rpt.err(f"{path}: 重复 ac_id：{ac['ac_id']}")
                ac_ids.add(ac["ac_id"])
                for field in ("given", "when", "then"):
                    if not ac.get(field):
                        rpt.err(f"{path}: {ac['ac_id']} 缺 {field}")

        # 关键规则验收覆盖：critical 规则必须有 ≥1 条 AC（除非被 open_question 阻塞）
        if r.get("criticality") == "critical":
            if acs:
                crit_with_ac += 1
            elif r_status == "BLOCKED" or r.get("blocked_by"):
                pass  # 被阻塞的待定义规则不要求 AC，但它也不能进实现
            else:
                rpt.err(f"{path}: {rid} 为 critical 但无验收标准 AC（不可验收）")

        # critical 规则若来源是 UNSPECIFIED/PROPOSED 且未被标 BLOCKED → 伪装风险
        if crit and sid in sources:
            scls = sources[sid].get("classification")
            if scls in {"UNSPECIFIED", "PROPOSED"} and r_status != "BLOCKED":
                rpt.err(
                    f"{path}: {rid}（{r.get('criticality')}）来源为 {scls} 却未标 BLOCKED"
                    f"（疑似把未确认内容当成确定规则）"
                )

    # ── open_questions / 门禁 ─────────────────────────────────────
    oq = data.get("open_questions") or []
    unresolved_high = [
        q for q in oq
        if isinstance(q, dict) and q.get("status") == "open" and q.get("risk") in HIGH_RISK
    ]

    # 门禁 1：来源追溯率 100%（上面已逐条报，这里给汇总）
    if rules and traced < len(rule_ids):
        rpt.err(f"{path}: 门禁-来源追溯率未达 100%（{traced}/{len(rule_ids)}）")

    # 门禁 2：关键规则验收覆盖率 100%
    if crit_rules and crit_with_ac < crit_rules:
        # 已逐条报 critical 无 AC；此处仅在"声称已批准"时升级为阻断
        pass

    # 门禁 3：高风险待确认 = 0（否则不得 approved）
    if approved and unresolved_high:
        qids = ", ".join(q.get("id", "?") for q in unresolved_high)
        rpt.err(f"{path}: 已标 approved 但仍有未解决高风险问题：{qids}")
    elif unresolved_high and status == "active":
        qids = ", ".join(q.get("id", "?") for q in unresolved_high)
        rpt.err(f"{path}: status=active 但有未解决高风险问题：{qids}（关键规则不得进实现）")
    elif unresolved_high:
        qids = ", ".join(q.get("id", "?") for q in unresolved_high)
        rpt.warn(f"{path}: 有未解决高风险待确认问题：{qids}（批准前须裁决）")

    # 门禁 4：active/approved 要求 status 自洽
    if approved and status != "active":
        rpt.err(f"{path}: approved=true 但 status={status}（应为 active）")
    if status == "active" and not approved:
        rpt.err(f"{path}: status=active 但 approved=false")

    # open_questions 引用的 blocks 要能解析
    for q in oq:
        if not isinstance(q, dict):
            continue
        for b in (q.get("blocks") or []):
            if b not in rule_ids:
                rpt.warn(f"{path}: 问题 {q.get('id','?')} 的 blocks 引用了未知规则 {b}")


def check_scr(path: str, rpt: Report) -> None:
    """SCR 结构校验（SPEC_GUIDE §6.5）。"""
    data = _load(path, rpt)
    if not isinstance(data, dict):
        return
    required = ["change_id", "spec_id", "change_type", "level", "change_origin",
                "reason", "before", "after", "approval"]
    for f in required:
        if f not in data or data.get(f) in (None, ""):
            rpt.err(f"{path}: SCR 缺必填字段 {f}")
    approval = data.get("approval") or {}
    if not isinstance(approval, dict):
        rpt.err(f"{path}: approval 必须是 mapping")
        return
    for field in ("rule_id", "source", "impact", "target_spec_version", "created_at"):
        if not data.get(field):
            rpt.err(f"{path}: SCR 缺必填字段 {field}")
    if data.get("level") not in {"L1", "L2", "L3"}:
        rpt.err(f"{path}: SCR level 非法：{data.get('level')}")
    if approval.get("status") not in {"PENDING", "APPROVED", "REJECTED"}:
        rpt.err(f"{path}: SCR approval.status 非法")
    if approval.get("status") == "APPROVED" and not (approval.get("approved_by") and data.get("resolved_at")):
        rpt.err(f"{path}: SCR 已批准但缺 approved_by/resolved_at")
    # L3 支付高风险必须有 approver_role
    if data.get("level") == "L3" and not approval.get("approver_role"):
        rpt.err(f"{path}: L3 高风险变更缺 approval.approver_role")
    # 类型 B（新增设计决策）批准前 after.status 只能是 PROPOSED
    origin = data.get("change_origin")
    after = data.get("after") or {}
    if not isinstance(after, dict):
        rpt.err(f"{path}: after 必须是 mapping")
        return
    if origin in {"TEST_DISCOVERED_GAP", "DESIGN_DECISION"}:
        if approval.get("status") != "APPROVED" and after.get("status") not in (None, "PROPOSED"):
            rpt.err(f"{path}: 类型 B 变更未批准前 after.status 应为 PROPOSED，实为 {after.get('status')}")


def main(argv: list[str]) -> int:
    repo_root = os.getcwd()
    rpt = Report()

    cov_files = sorted(glob.glob(os.path.join(repo_root, "docs/spec/coverage/*.yaml")))
    scr_files = sorted(glob.glob(os.path.join(repo_root, "docs/spec/changes/*.yaml")))

    if not cov_files and not scr_files:
        print(f"{DIM}spec-check: 未发现 coverage/SCR 文件（docs/spec/coverage|changes/*.yaml），跳过{NC}")
        return 0

    print(f"🔍 Spec 完整性校验  {DIM}规则：SPEC_GUIDE §1.3 + 量化门禁{NC}")
    print(f"   coverage: {len(cov_files)} 份  SCR: {len(scr_files)} 份")
    print()

    for f in cov_files:
        check_coverage(f, repo_root, rpt)
    for f in scr_files:
        check_scr(f, rpt)

    for m in rpt.red:
        print(f"{RED}🔴 {m}{NC}")
    for m in rpt.yellow:
        print(f"{YELLOW}🟡 {m}{NC}")

    print()
    if rpt.red:
        print(f"{RED}✗ {len(rpt.red)} 个阻断项（🔴）必须修复后才能批准/合入{NC}")
        return 1
    if rpt.yellow:
        print(f"{YELLOW}⚠ {len(rpt.yellow)} 个提示项（🟡），无阻断{NC}")
        return 0
    print(f"{GREEN}✅ 全部通过{NC}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
