import { positionsBox } from './helpers/positions';

class MarkerRenderer {
    constructor(data) {
        this._data = data;
    }

    draw(target) {
        target.useBitmapCoordinateSpace(scope => {
            const ctx = scope.context;
            const hRatio = scope.horizontalPixelRatio;
            const vRatio = scope.verticalPixelRatio;

            this._data.markers.forEach((marker) => {
                // Skip markers with invalid coordinates
                if (marker.x === null || marker.y === null) {
                    return;
                }

                const x = marker.x * hRatio;
                const y = marker.y * vRatio;
                const size = (marker.size || 6) * Math.min(hRatio, vRatio);
                const color = marker.color || '#2196F3';
                const shape = marker.shape || 'circle'; // 'circle', 'square', 'diamond', 'triangle', 'cross', 'x'

                ctx.fillStyle = color;
                ctx.strokeStyle = marker.borderColor || color;
                ctx.lineWidth = (marker.borderWidth || 1) * hRatio;

                // Draw marker based on shape
                ctx.beginPath();
                
                switch (shape) {
                    case 'circle':
                        ctx.arc(x, y, size, 0, Math.PI * 2);
                        break;
                    
                    case 'square':
                        ctx.rect(x - size, y - size, size * 2, size * 2);
                        break;
                    
                    case 'diamond':
                        ctx.moveTo(x, y - size);
                        ctx.lineTo(x + size, y);
                        ctx.lineTo(x, y + size);
                        ctx.lineTo(x - size, y);
                        ctx.closePath();
                        break;
                    
                    case 'triangle':
                        ctx.moveTo(x, y - size);
                        ctx.lineTo(x - size * 0.866, y + size * 0.5);
                        ctx.lineTo(x + size * 0.866, y + size * 0.5);
                        ctx.closePath();
                        break;
                    
                    case 'triangle-down':
                        ctx.moveTo(x, y + size);
                        ctx.lineTo(x - size * 0.866, y - size * 0.5);
                        ctx.lineTo(x + size * 0.866, y - size * 0.5);
                        ctx.closePath();
                        break;
                    
                    case 'cross':
                        // Draw a plus sign
                        const crossWidth = size * 0.3;
                        ctx.rect(x - crossWidth, y - size, crossWidth * 2, size * 2);
                        ctx.rect(x - size, y - crossWidth, size * 2, crossWidth * 2);
                        break;
                    
                    case 'x':
                        // Draw an X shape
                        const lineWidth = marker.borderWidth || 2;
                        ctx.lineWidth = lineWidth * hRatio;
                        ctx.moveTo(x - size, y - size);
                        ctx.lineTo(x + size, y + size);
                        ctx.moveTo(x + size, y - size);
                        ctx.lineTo(x - size, y + size);
                        ctx.stroke();
                        // Skip fill for X shape
                        return;
                    
                    case 'star':
                        // Draw a 5-point star
                        const spikes = 5;
                        const outerRadius = size;
                        const innerRadius = size * 0.5;
                        let rot = (Math.PI / 2) * 3;
                        const step = Math.PI / spikes;
                        
                        ctx.moveTo(x, y - outerRadius);
                        for (let i = 0; i < spikes; i++) {
                            ctx.lineTo(
                                x + Math.cos(rot) * outerRadius,
                                y + Math.sin(rot) * outerRadius
                            );
                            rot += step;
                            ctx.lineTo(
                                x + Math.cos(rot) * innerRadius,
                                y + Math.sin(rot) * innerRadius
                            );
                            rot += step;
                        }
                        ctx.lineTo(x, y - outerRadius);
                        ctx.closePath();
                        break;
                }

                // Fill the shape
                if (shape !== 'x') {
                    ctx.fill();
                }
                
                // Draw border if specified
                if (marker.borderWidth && shape !== 'x') {
                    ctx.stroke();
                }

                // Draw text label if provided
                if (marker.text) {
                    ctx.fillStyle = marker.textColor || '#ffffff';
                    ctx.font = `${9 * vRatio}px Arial`;
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'top';
                    ctx.shadowColor = 'rgba(0, 0, 0, 0.8)';
                    ctx.shadowBlur = 2 * vRatio;
                    ctx.fillText(marker.text, x, y + size + 4 * vRatio);
                    ctx.shadowBlur = 0;
                }
            });
        });
    }
}

class MarkerPaneView {
    constructor(source) {
        this._source = source;
        this._markers = [];
    }

    update() {
        const series = this._source._series;
        const timeScale = this._source._chart.timeScale();
        const chartData = this._source._chartData;
        
        // Get visible time range for culling
        const visibleRange = timeScale.getVisibleLogicalRange();
        let visibleTimeRange = null;
        
        if (visibleRange && chartData && chartData.length > 0) {
            const startIdx = Math.max(0, Math.floor(visibleRange.from));
            const endIdx = Math.min(chartData.length - 1, Math.ceil(visibleRange.to));
            
            if (startIdx < chartData.length && endIdx >= 0) {
                visibleTimeRange = {
                    from: chartData[startIdx].time,
                    to: chartData[endIdx].time
                };
            }
        }
        
        this._markers = this._source._markersData
            .filter((marker) => {
                // Skip markers outside visible time range (culling optimization)
                if (visibleTimeRange) {
                    if (marker.time < visibleTimeRange.from || marker.time > visibleTimeRange.to) {
                        return false;
                    }
                }
                return true;
            })
            .map((marker) => {
                let x = timeScale.timeToCoordinate(marker.time);
                const y = series.priceToCoordinate(marker.price);

                // Fallback: If timeToCoordinate returns null, use logical index
                if (x === null && chartData && chartData.length > 0) {
                    // Find logical index for the marker time
                    const findLogicalIndex = (time) => {
                        let left = 0;
                        let right = chartData.length - 1;
                        
                        while (left <= right) {
                            const mid = Math.floor((left + right) / 2);
                            if (chartData[mid].time === time) return mid;
                            if (chartData[mid].time < time) {
                                left = mid + 1;
                            } else {
                                right = mid - 1;
                            }
                        }
                        return left < chartData.length ? left : chartData.length - 1;
                    };
                    
                    const logical = findLogicalIndex(marker.time);
                    if (logical !== null) {
                        x = timeScale.logicalToCoordinate(logical);
                    }
                }

                return {
                    x,
                    y,
                    size: marker.size,
                    shape: marker.shape,
                    color: marker.color,
                    borderColor: marker.borderColor,
                    borderWidth: marker.borderWidth,
                    text: marker.text,
                    textColor: marker.textColor,
                };
            })
            .filter(marker => {
                // Skip markers with null coordinates
                return marker.x !== null && marker.y !== null;
            });
    }

    renderer() {
        return new MarkerRenderer({
            markers: this._markers || [],
        });
    }
}

export class MarkerPrimitive {
    constructor(chart, series, markersData, chartData) {
        this._chart = chart;
        this._series = series;
        this._markersData = markersData;
        this._chartData = chartData;
        this._paneViews = [new MarkerPaneView(this)];
    }

    updateAllViews() {
        this._paneViews.forEach(pv => pv.update());
    }

    paneViews() {
        return this._paneViews;
    }

    autoscaleInfo(startTimePoint, endTimePoint) {
        // Include marker prices in autoscale
        let minPrice = Infinity;
        let maxPrice = -Infinity;

        this._markersData.forEach(marker => {
            if (marker.price < minPrice) minPrice = marker.price;
            if (marker.price > maxPrice) maxPrice = marker.price;
        });

        if (minPrice === Infinity || maxPrice === -Infinity) {
            return null;
        }

        return {
            priceRange: {
                minValue: minPrice,
                maxValue: maxPrice,
            },
        };
    }
}

