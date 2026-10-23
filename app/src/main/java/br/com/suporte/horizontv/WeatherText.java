package br.com.suporte.horizontv;

final class WeatherText {
    private WeatherText() { }

    static String describe(int code) {
        switch (code) {
            case 0: return "Céu limpo";
            case 1: return "Predomínio de sol";
            case 2: return "Parcialmente nublado";
            case 3: return "Nublado";
            case 45:
            case 48: return "Neblina";
            case 51:
            case 53: return "Garoa leve";
            case 55: return "Garoa";
            case 56:
            case 57: return "Garoa congelante";
            case 61: return "Chuva fraca";
            case 63: return "Chuva moderada";
            case 65: return "Chuva forte";
            case 66:
            case 67: return "Chuva congelante";
            case 71: return "Neve fraca";
            case 73: return "Neve moderada";
            case 75: return "Neve intensa";
            case 77: return "Grãos de neve";
            case 80: return "Pancadas leves";
            case 81: return "Pancadas de chuva";
            case 82: return "Pancadas fortes";
            case 85:
            case 86: return "Pancadas de neve";
            case 95: return "Trovoadas";
            case 96:
            case 99: return "Trovoadas e granizo";
            default: return "Condição indisponível";
        }
    }
}
