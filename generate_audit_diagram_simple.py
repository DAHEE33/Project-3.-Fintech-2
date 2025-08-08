import requests
import base64

def generate_mermaid_image(mermaid_code, filename, format="png"):
    """
    Mermaid 코드를 이미지로 변환하여 저장
    """
    url = "https://mermaid.ink/img/"
    encoded_code = base64.b64encode(mermaid_code.encode()).decode()
    image_url = f"{url}{encoded_code}.{format}"
    
    try:
        response = requests.get(image_url)
        response.raise_for_status()
        
        with open(f"{filename}.{format}", "wb") as f:
            f.write(response.content)
        
        print(f"✅ {filename}.{format} 저장 완료!")
        return True
    except Exception as e:
        print(f"❌ 오류: {e}")
        return False

# 매우 간단한 감사/알람 흐름 다이어그램
audit_flow_diagram = """
graph LR
  UI[UI] --> C[AlarmController]
  C --> S[AlarmService]
  S --> N[NotificationService]
"""

if __name__ == "__main__":
    print("🎨 감사/알람 흐름 다이어그램 생성 중...")
    generate_mermaid_image(audit_flow_diagram, "06_감사_알람_흐름")
    print("완료!")
