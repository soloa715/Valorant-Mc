#!/usr/bin/env python3
"""
TypeSafe AI Code & Document Multi-Dimensional Evaluator CLI
Powered by TypeSafe System One API (Jev model)
"""

import os
import sys
import json
import argparse
from pathlib import Path
from typing import Dict, Any, List, Optional
import urllib.request
import urllib.error

TYPESAFE_API_URL = "https://api.typesafe.ai/v1/systemone"

def build_code_eval_questions() -> Dict[str, Any]:
    """
    Constructs the TypeSafe System One questions map across multiple dimensions:
    - quality_score (Score 1-5 primitive)
    - security_hazard (Noul Yes/No primitive)
    - architectural_layer (Choice primitive)
    - maintainability_score (Score 1-5 primitive)
    """
    return {
        "quality_score": {
            "type": "score",
            "instructions": "Rate the overall technical quality, structure, and clarity of this code.",
            "criteria": [
                "Poor / Unclear — High complexity, bad naming, syntax issues",
                "Below Average — Lacks structure, missing error handling",
                "Acceptable — Functional, readable, standard code structure",
                "Good / Well Structured — Clean, modular, strong typed patterns",
                "Exceptional / Production Grade — Outstanding design, spotless"
            ]
        },
        "security_hazard": {
            "type": "noul",
            "instructions": "Does this code contain potential security vulnerabilities, unsafe reflection, hardcoded credentials, or unvalidated inputs?",
            "criteria": {
                "true": "Contains security hazards or dangerous patterns",
                "false": "No obvious security vulnerabilities detected"
            }
        },
        "architectural_layer": {
            "type": "choice",
            "instructions": "Which architectural layer does this code primarily belong to?",
            "criteria": {
                "presentation_ui": "User interfaces, rendering, screens, widgets, HUDs",
                "business_logic": "Core rules, game logic, state machines, handlers",
                "data_payload": "Network packets, schemas, serialization, DTOs",
                "utility_helper": "Helper functions, math utilities, configuration, setup"
            }
        },
        "maintainability_score": {
            "type": "score",
            "instructions": "Rate the maintainability, modularity, and readability of this code.",
            "criteria": [
                "Unmaintainable — Spaghettified, tight coupling",
                "Needs Refactoring — Fragile dependencies or long methods",
                "Maintainable — Clean abstractions, standard structure",
                "Modular — Decoupled components, high reusability",
                "Exemplary — Pristine design, self-documenting code"
            ]
        }
    }

def call_typesafe_api(api_key: str, state_content: str, model: str = "jev-latest") -> Dict[str, Any]:
    """
    Sends a single batch request to TypeSafe System One API evaluating the state
    across all question primitives simultaneously.
    """
    payload = {
        "state": state_content,
        "model": model,
        "questions": build_code_eval_questions()
    }
    
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(TYPESAFE_API_URL, data=data, headers=headers, method="POST")
    
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        raise RuntimeError(f"TypeSafe API HTTP {e.code}: {err_body}")
    except Exception as e:
        raise RuntimeError(f"TypeSafe API Connection Error: {e}")

def simulate_typesafe_eval(file_path: Path, content: str) -> Dict[str, Any]:
    """
    Local System One simulation mode when running without an active API key.
    Calculates deterministic probabilities based on code length and static checks.
    """
    lines = content.splitlines()
    num_lines = len(lines)
    has_sec_keywords = any(kw in content.lower() for kw in ["password", "secret", "exec(", "unsafe", "eval("])
    
    # Infer layer
    path_str = str(file_path).lower()
    if "screen" in path_str or "gui" in path_str or "render" in path_str or "ui" in path_str:
        layer = "presentation_ui"
    elif "payload" in path_str or "packet" in path_str or "dto" in path_str or "data" in path_str:
        layer = "data_payload"
    elif "util" in path_str or "tool" in path_str or "config" in path_str:
        layer = "utility_helper"
    else:
        layer = "business_logic"

    quality_level = 4 if num_lines < 300 else (3 if num_lines < 800 else 2)
    
    return {
        "model": "jev-latest (simulated)",
        "answers": {
            "quality_score": {
                "type": "score",
                "score": float(quality_level),
                "confidence": 0.88,
                "probabilities": {"0": 0.05, "1": 0.05, "2": 0.10, "3": 0.30, "4": 0.50}
            },
            "security_hazard": {
                "type": "noul",
                "noul": 0.85 if has_sec_keywords else 0.04
            },
            "architectural_layer": {
                "type": "choice",
                "choice": layer,
                "confidence": 0.91,
                "probabilities": {
                    "presentation_ui": 0.70 if layer == "presentation_ui" else 0.10,
                    "business_logic": 0.70 if layer == "business_logic" else 0.10,
                    "data_payload": 0.70 if layer == "data_payload" else 0.10,
                    "utility_helper": 0.70 if layer == "utility_helper" else 0.10
                }
            },
            "maintainability_score": {
                "type": "score",
                "score": float(quality_level),
                "confidence": 0.85,
                "probabilities": {"0": 0.05, "1": 0.05, "2": 0.10, "3": 0.35, "4": 0.45}
            }
        },
        "usage": {"input_tokens": num_lines * 4, "output_tokens": 64}
    }

def print_evaluation_report(results: List[Dict[str, Any]]):
    """Prints a styled terminal evaluation summary report."""
    print("\n" + "=" * 80)
    print(" TYPESAFE SYSTEM ONE - CODE DOCUMENT EVALUATION REPORT")
    print(" Model: jev-latest | Batch Strategy: Speculative Fan-out")
    print("=" * 80)

    for res in results:
        file_name = res["file_name"]
        ans = res.get("answers", {})

        q_score = ans.get("quality_score", {}).get("score", 0.0)
        q_conf = ans.get("quality_score", {}).get("confidence", 0.0) * 100
        sec_noul = ans.get("security_hazard", {}).get("noul", 0.0)
        arch_choice = ans.get("architectural_layer", {}).get("choice", "unknown")
        m_score = ans.get("maintainability_score", {}).get("score", 0.0)

        sec_status = "[HAZARD DETECTED]" if sec_noul > 0.5 else "[SECURE]"

        print(f"\nFile: {file_name}")
        print(f" |-- Quality Rating        : {q_score:.1f} / 4.0  (Confidence: {q_conf:.1f}%)")
        print(f" |-- Maintainability Score : {m_score:.1f} / 4.0")
        print(f" |-- Architectural Layer   : [{arch_choice}]")
        print(f" \\-- Security Assessment   : {sec_status} (Hazard Prob: {sec_noul:.1%})")

    print("\n" + "=" * 80)
    print(f" Evaluated {len(results)} file(s) across 4 TypeSafe System One dimensions.")
    print("=" * 80 + "\n")

def main():
    parser = argparse.ArgumentParser(description="Evaluate code documents across dimensions using TypeSafe AI System One")
    parser.add_argument("paths", nargs="+", help="Files or directories to evaluate")
    parser.add_argument("--key", help="TypeSafe API Key (or set TYPESAFE_API_KEY environment variable)")
    parser.add_argument("--json", help="Export results to specified JSON file")

    args = parser.parse_args()

    api_key = args.key or os.getenv("TYPESAFE_API_KEY")

    if not api_key:
        print("[TypeSafe CLI] No TYPESAFE_API_KEY provided. Running in TypeSafe Local Simulation Mode.")

    files_to_eval = []
    for p_str in args.paths:
        p = Path(p_str)
        if p.is_file():
            files_to_eval.append(p)
        elif p.is_dir():
            for ext in ["*.py", "*.java", "*.ts", "*.js", "*.md", "*.json"]:
                files_to_eval.extend(p.glob(f"**/{ext}"))

    if not files_to_eval:
        print("❌ No matching documents found to evaluate.")
        sys.exit(1)

    # Limit max batch for CLI demo
    files_to_eval = files_to_eval[:15]

    eval_results = []
    for file_path in files_to_eval:
        try:
            content = file_path.read_text(encoding="utf-8", errors="ignore")
            if not content.strip():
                continue

            if api_key:
                res = call_typesafe_api(api_key, content[:4000])
            else:
                res = simulate_typesafe_eval(file_path, content)

            res["file_name"] = str(file_path)
            eval_results.append(res)
        except Exception as e:
            print(f"⚠️ Error evaluating {file_path}: {e}")

    print_evaluation_report(eval_results)

    if args.json:
        out_path = Path(args.json)
        out_path.write_text(json.dumps(eval_results, indent=2), encoding="utf-8")
        print(f"[OK] Exported evaluation results to: {out_path.resolve()}")

if __name__ == "__main__":
    main()
